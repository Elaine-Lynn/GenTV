# Mobile feedback contract

POST https://api.gentv.app/tv/feedback with Content-Type: application/json. Payload fields: app, type (bug/praise/feature/question), feedback, page, platform, version, device, os, email, user_id, source; optional screenshot_base64. User text and attachments are sent only after explicit submission in the feedback sheet.

Success requires HTTP 200/201/202 AND a JSON boolean success:true. HTML, malformed JSON, success:false, a missing success field, every redirect, and connection failures are failures. Client retains the draft on failure. Response body is bounded to 8 KiB. No redirect is followed.

The server owns validation, limits, retention, private storage and deletion handling. Its deployed configuration and tests are maintained by the server/ owner. Never embed a private server credential in the phone APK.
