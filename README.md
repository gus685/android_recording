# CallNote

Asks "Record this call?" via a heads-up notification when a call comes in (or reminds you
mid-call), records via the microphone (best with speakerphone), and saves to
Recordings/CallNote as `date_in|out_number_name.m4a`. Tap a recording (or the "saved"
notification) to share it to another app.

## Build
Push to GitHub; the Actions workflow builds `app-debug.apk` (artifact "CallNote-debug-apk").
Or open in Android Studio and run.

## Install
Copy the APK to the phone, allow "install unknown apps", open CallNote, tap
"Grant permissions". If the notification doesn't pop up: Settings > Notifications > CallNote
> "Record this call?" must be set to Pop on screen.
