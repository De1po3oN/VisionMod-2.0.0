#!/usr/bin/env bash
set -euo pipefail
export VISION_SACRED_WAR_DEMO=1
cvlc --ignore-config --no-one-instance --intf dummy --extraintf dbus --aout dummy --no-video --start-paused \
  "$RUNNER_TEMP/sacred-war/recording.mka" > sacred-war-player.log 2>&1 &
player=$!
trap 'kill "$player" 2>/dev/null || true' EXIT
for ((i=0;i<30;i++)); do
  if gdbus call --session --dest org.mpris.MediaPlayer2.vlc --object-path /org/mpris/MediaPlayer2 \
    --method org.freedesktop.DBus.Properties.Get org.mpris.MediaPlayer2.Player Metadata > sacred-war-mpris.log 2>&1; then
    if grep -q 'xesam:title' sacred-war-mpris.log; then break; fi
  fi
  sleep 0.25
done
grep -q 'xesam:title' sacred-war-mpris.log
exec_status=0
timeout --signal=TERM 140s gradle -PvisualGameTest runClientGameTest --no-daemon --console=plain --max-workers=2 > sacred-war-game.log 2>&1 || exec_status=$?
tail -n 28 sacred-war-game.log
exit "$exec_status"
