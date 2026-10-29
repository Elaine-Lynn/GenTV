"""Exercise real rollback shell logic against isolated filesystem and fake services."""
import hashlib,os,shutil,subprocess,sys,tarfile,tempfile,unittest
from pathlib import Path
@unittest.skipUnless(sys.platform.startswith('linux'),'Deployment uses GNU cp/mv on its Linux host')
class DeploymentRollbackTest(unittest.TestCase):
    def scenario(self,failure):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);bin=root/'bin';bin.mkdir();state=root/'fs';state.mkdir()
            script=Path(__file__).with_name('deploy')/'install-on-server.sh'
            source=script.read_text()
            for prefix in ('/srv/','/var/','/etc/'):
                source=source.replace(prefix,str(state)+prefix)
            paths=['/etc/gentv-wallpaper.env','/etc/nginx/sites-available/api.gentv.app','/etc/nginx/snippets/gentv-tv-api.locations.conf','/etc/nginx/snippets/gentv-trusted-proxies.conf','/etc/nginx/conf.d/gentv-limits.conf']
            paths += ['/etc/systemd/system/'+unit for unit in ('gentv-wallpaper.service','gentv-retention.service','gentv-retention.timer','gentv-backup.service','gentv-backup.timer','gentv-health.service','gentv-health.timer')]
            for path in paths:
                file=state/path.lstrip('/');file.parent.mkdir(parents=True,exist_ok=True);file.write_text('original '+path)
            old=state/'srv/gentv/wallpaper/releases/old';old.mkdir(parents=True)
            current=old.parent.parent/'current';current.symlink_to(old)
            (state/'var/tmp').mkdir(parents=True)
            payload=root/'payload';(payload/'deploy').mkdir(parents=True)
            (payload/'requirements.txt').write_text('')
            for unit in script.parent.iterdir():
                if unit.is_file():shutil.copyfile(unit,payload/'deploy'/unit.name)
            archive=root/'source.tar.gz'
            with tarfile.open(archive,'w:gz') as tar:
                for item in payload.iterdir():tar.add(item,arcname=item.name)
            helpers={
                'sudo':'#!/bin/sh\n[ "$1" != -n ] || shift\nexec "$@"\n',
                'systemctl':'#!/bin/sh\nexit 0\n',
                'nginx':'#!/bin/sh\nif [ "$FAIL_STAGE" = nginx ] && [ ! -f "$FAIL_MARKER" ]; then touch "$FAIL_MARKER"; exit 1; fi\nexit 0\n',
                'curl':'''#!/bin/sh
case "$*" in
 *api.cloudflare.com*) printf '{"success":true,"result":{"ipv4_cidrs":["172.16.0.0/24","172.16.1.0/24","172.16.2.0/24","172.16.3.0/24","172.16.4.0/24","172.16.5.0/24","172.16.6.0/24","172.16.7.0/24","172.16.8.0/24","172.16.9.0/24"],"ipv6_cidrs":[]}}'; exit 0;;
esac
if [ "$FAIL_STAGE" = health ] && [ ! -f "$FAIL_MARKER" ]; then touch "$FAIL_MARKER"; exit 22; fi
printf '{}'
''',
                'python3':'''#!/bin/sh
if [ "$1" = -m ] && [ "$2" = venv ]; then mkdir -p "$3/bin"; printf '#!/bin/sh\\nexit 0\\n' > "$3/bin/pip"; chmod +x "$3/bin/pip"; exit 0; fi
exec '''+shutil.which('python3')+''' "$@"
'''}
            for name,body in helpers.items():
                file=bin/name;file.write_text(body);file.chmod(0o755)
            runner=root/'run.sh';runner.write_text(source)
            process=subprocess.run(['sh',str(runner),str(archive),hashlib.sha256(archive.read_bytes()).hexdigest()],env=dict(os.environ,PATH=str(bin)+':'+os.environ['PATH'],FAIL_STAGE=failure,FAIL_MARKER=str(root/'failed')),capture_output=True,text=True)
            self.assertTrue((root/'failed').exists(),process.stdout+process.stderr)
            self.assertNotEqual(process.returncode,0,process.stdout+process.stderr)
            self.assertEqual(current.resolve(),old)
            for path in paths:self.assertEqual((state/path.lstrip('/')).read_text(),'original '+path)
            self.assertFalse((state/'var/tmp/gentv-wallpaper-deploy.lock').exists())
    def test_nginx_failure_restores_all_configuration(self):self.scenario('nginx')
    def test_post_switch_health_failure_restores_release_and_configuration(self):self.scenario('health')
