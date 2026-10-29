"""Ephemeral per-source quotas: no addresses written to analytics or logs."""
import collections
import hashlib
import secrets
import threading
import time
_KEY=secrets.token_bytes(32)
_LOCK=threading.Lock()
_WINDOWS={}
def limited(kind,environ):
    # Nginx replaces this header with its socket peer; never trust client forwarded headers.
    source=environ.get('HTTP_X_GENTV_PEER') if environ.get('REMOTE_ADDR') in ('127.0.0.1','::1') else None
    source=source or environ.get('REMOTE_ADDR','local')
    key=(kind,hashlib.blake2b(source.encode(),key=_KEY,digest_size=16).digest())
    now=time.monotonic()
    with _LOCK:
        for stale in [k for k,v in _WINDOWS.items() if not v or v[-1]<=now-60]: del _WINDOWS[stale]
        if key not in _WINDOWS:
            if len(_WINDOWS)>=10000:return True
            _WINDOWS[key]=collections.deque()
        window=_WINDOWS[key]
        while window and window[0]<=now-60:window.popleft()
        if len(window)>={'events':120,'feedback':12,'stats':60}[kind]:return True
        window.append(now)
        return False
