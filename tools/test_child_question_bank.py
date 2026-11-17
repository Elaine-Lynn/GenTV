"""Independent validation of the bundled question pack, not generator assertions."""
import ast
import copy
from child_question_bank import generate as generator
from collections import Counter
from fractions import Fraction
import hashlib
import json
from pathlib import Path
import re
import unittest

BANK=Path(__file__).resolve().parents[1]/'launcher/app/src/main/assets/child-questions'

def exact(expression):
    def read(node):
        if isinstance(node,ast.Constant):return Fraction(str(node.value))
        if isinstance(node,ast.UnaryOp) and isinstance(node.op,ast.USub):return -read(node.operand)
        if isinstance(node,ast.BinOp):
            a,b=read(node.left),read(node.right)
            if isinstance(node.op,ast.Add):return a+b
            if isinstance(node.op,ast.Sub):return a-b
            if isinstance(node.op,ast.Mult):return a*b
            if isinstance(node.op,ast.Div):return a/b
        raise AssertionError(ast.dump(node))
    expression=expression.replace('×','*').replace('÷','/').replace('−','-')
    expression=re.sub(r'(\d+)%',r'(\1/100)',expression)
    return read(ast.parse(expression.strip(),mode='eval').body)

class QuestionPackTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest=json.loads((BANK/'manifest.json').read_text())
        cls.rows=[json.loads(line) for file in cls.manifest['files'] for line in (BANK/file).read_text().splitlines()]

    def test_manifest_and_structural_integrity(self):
        ids=set();prompts=set()
        for filename,meta in self.manifest['files'].items():
            data=(BANK/filename).read_bytes()
            self.assertEqual(meta['sha256'],hashlib.sha256(data).hexdigest())
            self.assertEqual(meta['bytes'],len(data))
            self.assertEqual(meta['count'],len(data.splitlines()))
        for q in self.rows:
            self.assertNotIn(q['id'],ids);ids.add(q['id'])
            self.assertIn(q['grade'],range(1,7));self.assertIn(q['correct'],range(4))
            for locale in ('en','zh'):
                self.assertEqual(4,len(q['options_'+locale]))
                self.assertEqual(4,len(set(q['options_'+locale])))
                self.assertTrue(q['prompt_'+locale]);self.assertTrue(q['explanation_'+locale])
            key=(q['subject'],q['grade'],q['prompt_en'])
            self.assertNotIn(key,prompts);prompts.add(key)

    def test_all_math_answers_and_distractors_by_substitution(self):
        for q in self.rows:
            if q['subject']!='math':continue
            left,right=q['prompt_en'].split('=')
            correct=[]
            values=[Fraction(x) for x in q['options_en']]
            self.assertEqual(4,len(set(values)),q['id'])
            for i,option in enumerate(q['options_en']):
                if exact(left.replace('?',f'({option})'))==exact(right.replace('?',f'({option})')):correct.append(i)
            self.assertEqual([q['correct']],correct,q)
            self.assertEqual(Fraction(q['answer_exact']),values[q['correct']],q)

    def test_lower_grade_distractors_stay_in_the_number_domain(self):
        for q in self.rows:
            if q['subject']!='math':continue
            values=[Fraction(x) for x in q['options_en']]
            if q['grade']<=2:
                self.assertTrue(all(x.denominator==1 for x in values),q)
                self.assertTrue(all(0<=x<=(20 if q['grade']==1 else 100) for x in values),q)

    def test_six_grade_coverage_and_draft_release_gate(self):
        templates=self.manifest['templates']
        self.assertEqual({g:10 for g in range(1,7)},dict(Counter(t['grade'] for t in templates)))
        self.assertEqual({g:200 for g in range(1,7)},dict(Counter(q['grade'] for q in self.rows if q['subject']=='english')))
        self.assertEqual(300,len({q['lemma'] for q in self.rows if q['subject']=='english'}))
        ledger=json.loads((BANK.parents[5]/'tools/child_question_bank/reviews.json').read_text())
        for q in self.rows:
            if q['subject']=='english':
                self.assertEqual('reviewed' if q['id'] in ledger else 'draft',q['review_status'])
                if q['id'] in ledger:self.assertEqual(generator.content_digest(q),ledger[q['id']]['sha256'])
        self.assertEqual(generator.english_release_status(self.rows),self.manifest['english_release_status'])
        self.assertEqual({t['id'] for t in templates},{q['template_id'] for q in self.rows if q['subject']=='math'})

    def test_picture_questions_are_short_supported_and_preview_only(self):
        pictures=[q for q in self.rows if q['skill']=='picture_word']
        self.assertEqual({1:10,2:6},dict(Counter(q['grade'] for q in pictures)))
        for q in pictures:
            self.assertEqual('picture:'+q['lemma'],q['prompt_en'])
            self.assertEqual(q['prompt_en'],q['prompt_zh'])
            self.assertTrue((BANK.parents[1]/'res/drawable'/('child_picture_'+q['lemma']+'.xml')).exists())
            self.assertIn('case "picture:'+q['lemma']+'"', (BANK.parents[1]/'java/com/gentv/tv/ChildQuizView.java').read_text())
            self.assertEqual(q['lemma'],q['options_en'][q['correct']])
            self.assertTrue(all(len(x)<=10 for x in q['options_en']))

    def test_english_keys_match_source(self):
        source={}
        for line in (BANK.parents[5]/'tools/child_question_bank/vocabulary.tsv').read_text().splitlines():
            if line and not line.startswith('#'):
                grade,word,zh,definition=line.split('|');source[word]=(zh,definition)
        for q in self.rows:
            if q['subject']!='english':continue
            word=q['lemma'];zh,definition=source[word];i=q['correct']
            self.assertEqual(definition if q['skill']=='word_to_meaning' else word,q['options_en'][i])
            self.assertEqual(zh if q['skill']=='word_to_meaning' else word,q['options_zh'][i])

class ReviewGateTest(unittest.TestCase):
    def setUp(self):
        self.rows = generator.english_questions()[:4]

    def review(self, row):
        # Synthetic fixture only; never written to the approval ledger.
        return dict(reviewer='Test fixture', reviewed_at='2026-11-05',
                    sha256=generator.content_digest(row))

    def test_valid_reviews_enable_only_the_selected_grade_at_three(self):
        for count in (0, 1, 2, 3):
            rows = copy.deepcopy(self.rows)
            generator.apply_reviews(rows, {q['id']: self.review(q) for q in rows[:count]})
            status = generator.english_release_status(rows)
            self.assertEqual(count, status['1']['reviewed'])
            self.assertEqual('pack' if count >= 3 else 'legacy-18', status['1']['formal_source'])
            self.assertEqual('legacy-18', status['2']['formal_source'])

    def test_content_changes_invalidate_approval(self):
        for field in ('prompt_en', 'prompt_zh', 'options_en', 'correct', 'grade', 'skill'):
            rows = copy.deepcopy(self.rows)
            ledger = {rows[0]['id']: self.review(rows[0])}
            rows[0][field] = 'changed'
            with self.subTest(field=field), self.assertRaises(ValueError):
                generator.apply_reviews(rows, ledger)

    def test_invalid_ledger_is_rejected_without_partial_approval(self):
        first, second = self.rows[:2]
        invalid = [None, {}, dict(self.review(second), reviewer='  '),
                   dict(self.review(second), reviewer=123),
                   dict(self.review(second), reviewed_at='yesterday'),
                   dict(self.review(second), reviewed_at='2026-02-30'),
                   dict(self.review(second), reviewed_at='20261105')]
        for review in invalid:
            rows = copy.deepcopy(self.rows)
            with self.subTest(review=review), self.assertRaises(ValueError):
                generator.apply_reviews(rows, {first['id']: self.review(first), second['id']: review})
            self.assertTrue(all(q['review_status'] == 'draft' for q in rows))
        with self.assertRaises(ValueError):
            generator.apply_reviews(self.rows, {'unknown': self.review(first)})
        with self.assertRaises(ValueError):
            generator.apply_reviews(self.rows, [])
        math = dict(first, subject='math')
        with self.assertRaises(ValueError):
            generator.apply_reviews([math], {math['id']: self.review(math)})

if __name__=='__main__':unittest.main()
