#!/bin/bash
# usage: pixel-mem.sh <context>...: replays the Pixel chat at each context, sampling storage reads and memory.
ADB=~/android-sdk/platform-tools/adb; S="-s 66241FDKY001PL"; cd /mnt/data/dev/bruce
for C in "$@"; do
  $ADB $S shell am force-stop com.bizzeh.bruce; sleep 5
  : > build/scratch/replay-pixel-$C.log; $ADB $S logcat -c
  $ADB $S shell am instrument -w -e class com.bizzeh.bruce.runtime.ChatReplayDeviceTest -e model MiniCPM5-2B-Q4_K_M.gguf -e context $C -e accepted search_wikipedia,fetch_page -e seeds 0 -e runs 1 -e label pixel-mem-$C com.bizzeh.bruce.test/androidx.test.runner.AndroidJUnitRunner > build/scratch/replay-pixel-$C.log 2>&1 &
  sleep 4; P=$($ADB $S shell pidof com.bizzeh.bruce)
  first=""; maxswap=0; minfile=99999999
  while ! grep -q 'OK (\|FAILURES\|crashed' build/scratch/replay-pixel-$C.log; do
    read mf anon file swap avail < <($ADB $S shell "cat /proc/$P/stat 2>/dev/null | cut -d' ' -f12; grep -E 'RssAnon|RssFile|VmSwap' /proc/$P/status 2>/dev/null | tr -s ' ' | cut -d' ' -f2; grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2" | tr '\n' ' ')
    [ -n "$mf" ] && { [ -z "$first" ] && first=$mf; last=$mf; [ "${swap:-0}" -gt $maxswap ] && maxswap=$swap; [ "${file:-0}" -lt $minfile ] && minfile=$file; }
    sleep 5
  done
  kill=$($ADB $S logcat -d | grep -c "Killing $P:com.bizzeh.bruce")
  done_line=$($ADB $S logcat -d -s BruceReplay:I | grep '"type":"done"' | grep -o '"wall_ms":[0-9]*')
  steps=$($ADB $S logcat -d -s BruceReplay:I | grep -c '"type":"step"')
  echo "ctx=$C majflt_during=$((last-first)) max_swap_kb=$maxswap min_weights_rss_kb=$minfile killed=$kill steps=$steps $done_line"
done
echo ALL DONE
