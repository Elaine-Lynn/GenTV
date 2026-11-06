"""Generate original offline practice assets deterministically; no network or dependencies."""
import ast
from collections import Counter
from fractions import Fraction as F
import hashlib
import json
from pathlib import Path
import random

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'launcher/app/src/main/assets/child-questions'
VERSION = '1.0.1'
PICTURE_WORDS = {'apple', 'dog', 'chair', 'fish', 'book', 'ball', 'sun', 'moon', 'tree', 'cat',
                 'blue', 'red', 'green', 'yellow', 'egg', 'bread'}

def evaluate(expression):
    def walk(node):
        if isinstance(node, ast.Constant) and isinstance(node.value, int): return F(node.value)
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.USub): return -walk(node.operand)
        if isinstance(node, ast.BinOp):
            a,b=walk(node.left),walk(node.right)
            if isinstance(node.op,ast.Add):return a+b
            if isinstance(node.op,ast.Sub):return a-b
            if isinstance(node.op,ast.Mult):return a*b
            if isinstance(node.op,ast.Div):return a/b
        raise ValueError(expression)
    return walk(ast.parse(expression,mode='eval').body)

def number(value, decimal=False):
    value=F(value)
    if value.denominator==1:return str(value.numerator)
    d=value.denominator
    for p in (2,5):
        while d%p==0:d//=p
    if decimal and d==1:
        from decimal import Decimal,localcontext
        with localcontext() as ctx:
            ctx.prec=30
            return format(Decimal(value.numerator)/Decimal(value.denominator),'f').rstrip('0').rstrip('.')
    return f'{value.numerator}/{value.denominator}'

def templates():
    # Arithmetic expressions contain only exact integer/fraction operations.
    rows=[]
    def add(g,skill,expr,display=None,decimal=False):
        rows.append(dict(id=f'math-g{g}-{sum(x["grade"]==g for x in rows)+1:02}',grade=g,
                         skill=skill,expression=expr,display=display or expr+' = ?',decimal=decimal))
    add(1,'add-within-10','a+b');add(1,'subtract-within-10','a-b')
    add(1,'add-within-20','a+b');add(1,'subtract-within-20','a-b')
    add(1,'missing-addend','a','? + {b} = {a+b}')
    add(1,'missing-subtrahend','b','{a+b} − ? = {a}')
    add(1,'add-three','a+b+c');add(1,'add-then-subtract','a+b-c')
    add(1,'subtract-then-add','a-b+c');add(1,'missing-three-term','c','{a} + ? + {b} = {a+b+c}')
    add(2,'two-digit-add','a+b');add(2,'two-digit-subtract','a-b')
    add(2,'times-table','u*v');add(2,'exact-division','u','{u*v} ÷ {v} = ?')
    add(2,'missing-factor','v','{u} × ? = {u*v}')
    add(2,'add-three','u+v+w');add(2,'subtract-two','a-u-v')
    add(2,'multiply-then-add','u*v+w');add(2,'multiply-then-subtract','u*v-w')
    add(2,'divide-then-add','u+w','{u*v} ÷ {v} + {w} = ?')
    add(3,'three-digit-add','a+b');add(3,'three-digit-subtract','a-b')
    add(3,'two-digit-times-one-digit','p*u');add(3,'three-digit-times-one-digit','a*u')
    add(3,'exact-division','p','{p*u} ÷ {u} = ?')
    add(3,'multiply-add','p*u+v');add(3,'multiply-subtract','p*u-v')
    add(3,'parentheses-times','(p+u)*v');add(3,'parentheses-divide','u+v','({u*w} + {v*w}) ÷ {w} = ?')
    add(3,'missing-dividend','p*u','? ÷ {u} = {p}')
    add(4,'two-digit-multiplication','p*q');add(4,'three-by-two-multiplication','a*q')
    add(4,'divide-by-two-digits','p','{p*q} ÷ {q} = ?')
    add(4,'two-products','p*u+q*v');add(4,'multiply-parentheses','(p+q)*u')
    add(4,'divide-parentheses','u+v','({p*u} + {p*v}) ÷ {p} = ?')
    add(4,'decimal-add','d+e',decimal=True);add(4,'decimal-subtract','d-e',decimal=True)
    add(4,'decimal-missing-addend','e','? + {d} = {d+e}',True)
    add(4,'decimal-three-terms','d+e-f',decimal=True)
    add(5,'decimal-times-integer','d*u',decimal=True)
    add(5,'decimal-multiplication','d*e',decimal=True)
    add(5,'decimal-divide-integer','d','{d*u} ÷ {u} = ?',True)
    add(5,'decimal-division','u','{d*u} ÷ {d} = ?',True)
    add(5,'like-denominator-add','r+s','{j}/{n} + {k}/{n} = ?');add(5,'like-denominator-subtract','r-s','{j}/{n} − {k}/{n} = ?')
    add(5,'unlike-denominator-add','r+t');add(5,'unlike-denominator-subtract','r-t')
    add(5,'fraction-missing-addend','t','? + {r} = {r+t}')
    add(5,'decimal-parentheses','(d+e)*u',decimal=True)
    add(6,'fraction-multiplication','r*t');add(6,'fraction-division','r/t')
    add(6,'integer-times-fraction','u*r');add(6,'integer-divide-fraction','u/r')
    add(6,'fraction-parentheses','(r+s)*t')
    add(6,'percentage-of-integer','a*h/100','{a} × {h}% = ?')
    add(6,'percentage-increase','a*(100+h)/100','{a} × (1 + {h}%) = ?')
    add(6,'percentage-decrease','a*(100-h)/100','{a} × (1 − {h}%) = ?')
    add(6,'fraction-missing-factor','t','{r} × ? = {r*t}')
    add(6,'fraction-divide-sum','r/(s+t)')
    return rows

def render(text,params,decimal):
    if '{' in text:
        import re
        text=re.sub(r'\{([^{}]+)\}',lambda m:number(evaluate_params(m[1],params),decimal),text)
    else:
        import re
        text=text.replace('/', ' ÷ ').replace('-', ' − ')
        text=re.sub(r'\b[a-z]\b',lambda m:'('+number(params[m[0]],decimal)+')' if params[m[0]].denominator!=1 else number(params[m[0]],decimal),text)
    return text.replace('*',' × ').replace('/','/') .replace(' - ',' − ')

def evaluate_params(expr,params):
    import re
    expanded=re.sub(r'\b[a-z]\b',lambda m:'('+str(params[m[0]].numerator)+'/'+str(params[m[0]].denominator)+')',expr)
    return evaluate(expanded)

def math_questions():
    rows=[];seen=set()
    for template in templates():
        rng=random.Random(template['id']);g=template['grade'];count=0
        for trial in range(12000):
            p={k:F(rng.randint(2,9)) for k in 'uvw'}
            p.update(a=F(rng.randint(100,800)),b=F(rng.randint(100,800)),c=F(rng.randint(1,6)),p=F(rng.randint(12,89)),q=F(rng.randint(12,39)),h=F(rng.choice([5,10,15,20,25,30,40,50,60,75])))
            denominator=rng.choice([4,6,8,10,12]);p.update(r=F(rng.randint(1,denominator-1),denominator),s=F(rng.randint(1,denominator-1),denominator),t=F(rng.randint(1,8),rng.choice([3,5,6,9])))
            p.update(n=F(denominator),j=F(rng.randint(1,denominator-1)),k=F(rng.randint(1,denominator-1)))
            if template['skill'].startswith('like-denominator'):p.update(r=p['j']/p['n'],s=p['k']/p['n'])
            p.update(d=F(rng.randint(11,399),100),e=F(rng.randint(11,199),100),f=F(rng.randint(1,99),100))
            if g==1:
                p.update(a=F(rng.randint(1,9)),b=F(rng.randint(1,9)))
                if template['skill'].startswith('subtract'):p['a']=F(rng.randint(1,10) if '10' in template['skill'] else rng.randint(11,20))
            if g==2:p.update(a=F(rng.randint(10,90)),b=F(rng.randint(10,90)))
            answer=evaluate_params(template['expression'],p)
            if answer<0 or g==1 and answer>20:continue
            if template['skill']=='subtract-then-add' and p['a']<p['b']:continue
            if template['skill']=='add-within-10' and answer>10:continue
            if template['skill']=='two-digit-add' and answer>100:continue
            if template['skill'] in ('like-denominator-subtract','unlike-denominator-subtract','decimal-subtract') and answer<=0:continue
            # Bound every intermediate shown in first-grade chains to the intended range.
            if g==1 and (p['a']+p['b']+ (p['c'] if 'three' in template['skill'] else 0)>20):continue
            prompt=render(template['display'],p,template['decimal']).replace('*','×')
            prompt=prompt.replace(' + ',' + ').replace(' - ',' − ').replace(' / ',' ÷ ')
            fingerprint=(g,prompt)
            if fingerprint in seen:continue
            seen.add(fingerprint)
            step=F(1,100) if template['decimal'] else F(1,answer.denominator)
            candidates=[answer+step*x for x in [-1,1,-2,2,-10,10] if answer+step*x>=0]
            # Include common scale/sign/order mistakes, excluding mathematical equivalents.
            candidates.extend([answer*10,answer/10,answer+1,answer*2])
            wrong=[]
            for value in candidates:
                if answer.denominator==1 and not template['decimal'] and value.denominator!=1:continue
                if g<=2 and value>(20 if g==1 else 100):continue
                if value!=answer and value not in wrong:wrong.append(value)
            if len(wrong)<3:raise ValueError('Insufficient distractors: '+template['id'])
            rng.shuffle(wrong);opts=[answer]+wrong[:3];rng.shuffle(opts)
            rows.append(dict(id=f'{template["id"]}-{count+1:03}',subject='math',grade=g,skill=template['skill'],template_id=template['id'],version=VERSION,review_status='automated_verified',prompt_en=prompt,prompt_zh=prompt,options_en=[number(x,template['decimal']) for x in opts],options_zh=[number(x,template['decimal']) for x in opts],correct=opts.index(answer),answer_exact=str(answer),explanation_en='Substitute the answer and check both sides of the equation.',explanation_zh='代入答案，核对等式两边是否相等。',source='original-generated'))
            count+=1
            if count==200:break
        if count<10:raise ValueError((template['id'],count))
    return rows

def vocabulary():
    data=[]
    for line in Path(__file__).with_name('vocabulary.tsv').read_text().splitlines():
        if line and not line.startswith('#'):
            g,w,z,d=line.split('|');data.append(dict(grade=int(g),word=w,zh=z,definition=d))
    assert Counter(x['grade'] for x in data)=={g:50 for g in range(1,7)}
    return data

def english_questions():
    data=vocabulary();known={x['word'] for x in data};result=[]
    contexts=dict(line.split('|',1) for line in Path(__file__).with_name('contexts.tsv').read_text().splitlines() if line and not line.startswith('#'))
    for item in data:
        g=item['grade'];w=item['word'];z=item['zh'];d=item['definition'];rng=random.Random('english:'+w)
        others=[x for x in data if x['grade']==g and x['word']!=w];rng.shuffle(others);other=others[:3]
        for kind in ['meaning_to_word','word_to_meaning','definition','spelling']:
            pairs=[(w,w)]+[(x['word'],x['word']) for x in other]
            en=f'Which English word means "{d}"?';zh=f'“{z}”对应哪个英文单词？'
            if kind=='meaning_to_word' and g <= 2 and w in PICTURE_WORDS:
                en=zh='picture:'+w
            if kind=='word_to_meaning':
                en=f'What does "{w}" mean?';zh=f'“{w}”是什么意思？'
                pairs=[(d,z)]+[(x['definition'],x['zh']) for x in other]
            if kind=='definition':
                en=f'Choose the word for this definition: {d}.';zh=f'根据英文释义选词：{d}。'
            if kind=='definition' and w in contexts:
                en='Complete the sentence: '+contexts[w]
                zh='根据上下文选词：'+contexts[w]
            if kind=='spelling':
                en=f'Choose the correctly spelled word meaning "{d}".';zh=f'选择拼写正确、表示“{z}”的英文词。'
                mutations=[]
                for i,ch in enumerate(w):
                    if ch==' ':continue
                    for candidate in (w[:i]+w[i+1:],w[:i]+ch+w[i:],w[:i]+'x'+w[i+1:]):
                        if candidate and candidate not in known and candidate not in mutations:mutations.append(candidate)
                rng.shuffle(mutations);pairs=[(w,w)]+[(x,x) for x in mutations[:3]]
            correct_pair=pairs[0];rng.shuffle(pairs)
            result.append(dict(id=f'english-g{g}-{w.replace(" ","-")}-{kind}',subject='english',grade=g,skill=('picture_word' if kind=='meaning_to_word' and g <= 2 and w in PICTURE_WORDS else 'context_cloze' if kind=='definition' and w in contexts else kind),lemma=w,version=VERSION,review_status='draft',prompt_en=en,prompt_zh=zh,options_en=[x[0] for x in pairs],options_zh=[x[1] for x in pairs],correct=pairs.index(correct_pair),explanation_en=f'{w}: {d}.',explanation_zh=f'{w}：{z}。',source='original-authored-ai-assisted'))
    return result

def content_digest(row):
    base = dict(row)
    base.pop('review', None)
    base['review_status'] = 'draft'
    return hashlib.sha256(json.dumps(base, ensure_ascii=False, sort_keys=True,
                                     separators=(',', ':')).encode()).hexdigest()


def apply_reviews(records, reviews):
    """Validate the entire ledger before changing any row; never infer approval."""
    from datetime import date
    if not isinstance(reviews, dict):
        raise ValueError('Reviews must be an object keyed by question ID')
    by_id = {row['id']: row for row in records}
    unknown = set(reviews) - set(by_id)
    if unknown:
        raise ValueError('Unknown reviewed question IDs: ' + str(sorted(unknown)))
    for question_id, review in reviews.items():
        row = by_id[question_id]
        if not isinstance(review, dict) or row['subject'] != 'english':
            raise ValueError('Invalid review: ' + question_id)
        reviewer, reviewed_at = review.get('reviewer'), review.get('reviewed_at')
        if not isinstance(reviewer, str) or not reviewer.strip():
            raise ValueError('Missing reviewer: ' + question_id)
        try:
            if not isinstance(reviewed_at, str) or date.fromisoformat(reviewed_at).isoformat() != reviewed_at:
                raise ValueError()
        except (ValueError, TypeError):
            raise ValueError('Review date must be YYYY-MM-DD: ' + question_id) from None
        if review.get('sha256') != content_digest(row):
            raise ValueError('Invalid or stale review: ' + question_id)
    for question_id, review in reviews.items():
        by_id[question_id]['review_status'] = 'reviewed'
        by_id[question_id]['review'] = dict(review)


def english_release_status(records):
    result = {}
    for grade in range(1, 7):
        rows = [q for q in records if q['subject'] == 'english' and q['grade'] == grade]
        approved = [q for q in rows if q['review_status'] == 'reviewed']
        result[str(grade)] = dict(total=len(rows), reviewed=len(approved),
                                 draft=len(rows) - len(approved),
                                 formal_source='pack' if len(approved) >= 3 else 'legacy-18',
                                 reviewed_skills=dict(sorted(Counter(q['skill'] for q in approved).items())))
    return result


def generate():
    records=math_questions()+english_questions()
    reviews=json.loads(Path(__file__).with_name('reviews.json').read_text())
    apply_reviews(records, reviews)
    OUT.mkdir(parents=True,exist_ok=True)
    files={}
    for old in OUT.glob('*.jsonl'):old.unlink()
    for subject in ('math','english'):
        for grade in range(1,7):
            path=OUT/f'{subject}-g{grade}.jsonl'
            subset=[x for x in records if x['subject']==subject and x['grade']==grade]
            content=''.join(json.dumps(x,ensure_ascii=False,separators=(',',':'))+'\n' for x in subset)
            path.write_text(content)
            files[path.name]=dict(sha256=hashlib.sha256(content.encode()).hexdigest(),bytes=len(content.encode()),count=len(subset))
    manifest=dict(schema_version=1,version=VERSION,files=files,templates=templates(),english_lemmas=300,english_release_status=english_release_status(records),release_policy='Draft English available in parent preview only; formal rounds retain bundled legacy questions until review.',counts={s:{str(g):sum(x['subject']==s and x['grade']==g for x in records) for g in range(1,7)} for s in ('math','english')})
    (OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    import csv
    review_dir=ROOT/'output/child-question-bank'
    review_dir.mkdir(parents=True,exist_ok=True)
    with (review_dir/'english-review.csv').open('w',encoding='utf-8-sig',newline='') as output:
        columns=['id','grade','lemma','skill','prompt_zh','prompt_en','options_zh','options_en','correct_answer_zh','correct_answer_en','explanation_zh','review_status','review_sha256']
        writer=csv.DictWriter(output,fieldnames=columns);writer.writeheader()
        for row in records:
            if row['subject']!='english':continue
            fingerprint=content_digest(row)
            item={key:row[key] for key in columns if key in row}
            item.update(options_zh=' / '.join(row['options_zh']),options_en=' / '.join(row['options_en']),correct_answer_zh=row['options_zh'][row['correct']],correct_answer_en=row['options_en'][row['correct']],review_sha256=fingerprint)
            writer.writerow(item)
    print(json.dumps(manifest['counts'],ensure_ascii=False));print('templates',len(templates()))

if __name__=='__main__':generate()
