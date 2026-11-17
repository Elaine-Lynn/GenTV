"""Generate OS-native setup scripts with release-bound URLs and hashes, no Python dependency."""
import json


def write_bootstraps(skill, lock):
    runtimes = lock.get('runtimes', [lock.get('runtime')])
    shell = ['#!/bin/sh', 'set -eu', 'dest=${1:?Usage: prepare.sh NEW_DIRECTORY [LOCAL_RUNTIME_ZIP]}',
             '[ ! -e "$dest" ] || { echo "Destination already exists" >&2; exit 1; }',
             'case "$(uname -s):$(uname -m)" in']
    for r in runtimes:
        if r['platform'] == 'Windows': continue
        system = {'Darwin':'Darwin', 'Linux':'Linux'}[r['platform']]
        shell.append(f"  {system}:{r['architecture']}) file='{r['file']}'; hash='{r['sha256']}'; directory='{r['directory']}' ;;")
    shell += ['  *) echo "No matching runtime in this release" >&2; exit 1 ;;', 'esac',
              'tmp=$(mktemp -d)', 'trap \'rm -rf "$tmp"\' EXIT HUP INT TERM',
              'if [ "$#" -ge 2 ]; then cp "$2" "$tmp/runtime.zip"; else',
              '  curl --fail --location --proto =https --proto-redir =https --max-time 300 --output "$tmp/runtime.zip" "'+lock['release_url']+'/$file"', 'fi',
              'if command -v sha256sum >/dev/null 2>&1; then observed=$(sha256sum "$tmp/runtime.zip" | cut -d " " -f 1); else observed=$(shasum -a 256 "$tmp/runtime.zip" | cut -d " " -f 1); fi',
              '[ "$observed" = "$hash" ] || { echo "SHA-256 mismatch" >&2; exit 1; }',
              'mkdir -p "$dest"', 'unzip -q "$tmp/runtime.zip" -d "$dest"', 'printf "%s\\n" "$dest/$directory"']
    scripts = skill/'scripts'; scripts.mkdir(exist_ok=True)
    (scripts/'prepare.sh').write_text('\n'.join(shell)+'\n')
    windows = [r for r in runtimes if r['platform'] == 'Windows']
    ps = ['param([Parameter(Mandatory=$true)][string]$Destination,[string]$Bundle)', "$ErrorActionPreference = 'Stop'", "if (Test-Path -LiteralPath $Destination) { throw 'Destination already exists' }"]
    if windows:
        r=windows[0]
        ps += ["if ([System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString() -ne 'X64') { throw 'This release requires Windows x64' }",
               "$tmp = Join-Path ([IO.Path]::GetTempPath()) ([Guid]::NewGuid().ToString() + '.zip')", 'try {',
               '  if ($Bundle) { Copy-Item -LiteralPath $Bundle -Destination $tmp } else {',
               "    Invoke-WebRequest -Uri '"+lock['release_url']+'/'+r['file']+"' -OutFile $tmp", '  }',
               "  if ((Get-FileHash -LiteralPath $tmp -Algorithm SHA256).Hash.ToLowerInvariant() -ne '"+r['sha256']+"') { throw 'SHA-256 mismatch' }",
               '  Expand-Archive -LiteralPath $tmp -DestinationPath $Destination',
               "  Write-Output (Join-Path $Destination '"+r['directory']+"')", '} finally { Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue }']
    else: ps += ["throw 'No Windows runtime in this release'"]
    (scripts/'prepare.ps1').write_text('\n'.join(ps)+'\n')
