# List review for the b06ab21 bump (upstream main after v3.0.0)

The 43 shipped lists that changed between 3e34f40 (v2.7.0) and b06ab21, checked package by package
against the denylist (`Denylist.kt`), each profile's never-disable list and the list header's KEEP lines.
Rule for new rows (as in the 2.7 bump): ads and telemetry are default-off only when the header says the
set that the profile matches was tested with them off; everything else is an ask row. The reasons for
every departure from upstream are in `core/spec/upstream-feedback/curation-notes.tsv`.

The parser fix (C2) that came with the bump changes no package in any list: a commented-out package line
no longer splits a group, so the groups titled with a kept line are gone (25 curated titles in 11
profiles), and the three unchanged lists
where that moved a row (amazon-fire-tv-stick-4k-2018, panasonic-4ktv-0su, tcl-beyondtv-rt2851) keep
every row where it was. The 68 new folders are parked, and the 105 unchanged lists convert byte for byte
as before.

No changed list names a new firmware updater (the D22 rule already covers every updater-like name in the
shipped lists) or a new setup wizard, so `ProfileCategories.kt` and `Denylist.kt` are unchanged.

| List | What changed at b06ab21 | Verdict |
|-|-|-|
| amazon-fire-tv-afttiff43 | second unit (75" Omni) and a new group of 13 rows, tested there with a reboot | ads and telemetry rows default-off; notices, Wi-Fi password sync, Fire TV Channels and the print service plugin ask rows; the print spooler (reviewed) default-off; easyupgrade a Software updates row (D22) |
| amazon-fire-tv-stick-4k-2023 | a Fire OS 8.1.7.4 unit's 22 package subset as comments, HANDLERS_ALLOW note | no package change; kept lines no longer split groups; no change to the profile |
| benq-e55-735 | Undo line | header only |
| byintek-t5-max | wording of the setupwraith note | header only |
| google-chromecast-gtv-4k | setupwraith can own a saved network on the 4K too; adb notes | header only; the home switch stays skipped for Google hardware (casting) |
| google-tv-streamer-4k-kirkwood | troubleshooting link | header only |
| hisense-hg9 | second and third units, smartsense trio checked over reboots | header only; setupwraith kept there, the takeover's Wi-Fi guard already handles it |
| hisense-hismarttv-a4-android11 | second unit; reboot timing note | header only |
| hisense-smarttv-4k-ffm-android11 | second and third units; their extra rows stay comments | header only |
| hisense-u75-hengshan | second and third units | header only |
| insignia-ns55f301na25 | PowerShell Undo line | header only |
| kalley-r10g | launcherx and setupwraith now kept on (#1807: boot loop with both off on a second set) | both never-disable, STOCK_LAUNCHER_KEEP_ENABLED; the takeover quirk stays so the wizard is only put back with the TV's own home screen in charge; HomeSwitchRisk skips the switch for the family; ProfilesTest checks it |
| motorola-swtv-24ae-fhd | second unit (Coocaa, SEA) and fusion.android.tv.demo | fusion.android.tv.demo an ask row: it answers the Input key on other MediaTek sets and the header does not test that |
| ok-ai-pont-ikebukuro | second unit (Nikkei DVB91NK) and batch 5, reboot-checked there | partner setup and the print spooler default-off, the other extras and the USB players ask rows; still under review for the brand sets |
| philips-50pud7406 | third unit (55PUS7906), one evening; its candidates stay comments | header only |
| philips-50pus8517 | second unit (70PUS8505), one day | header only |
| philips-r8g-google-tv | standby light link | header only |
| prismplus-q65-ultra-r10g | Privacy Sandbox group, feedbackconsent and setupwraith off (PR #1146; the reboot check is in the PR thread, not the header) | the five lines are left out of the profile (leftOut), so it is the 0.4.0 profile (only the list digest differs): the Privacy Sandbox rows until the header records the tested-off reboot, the wizard because the switch is skipped for the MOKA family and the same board boot-looped with it off |
| rca-ai-pont | CHiQ and ERGO variants, with "Keep X" notes | no package change; the header keep notes add the OTA, USB media, mirroring and TTS packages to never-disable, so com.homwee.mmp.fileexplorer is in the protected report (never switched off) |
| skyworth-sw-22sa | Undo line | header only |
| sony-bravia-4k-vh2 | third, fourth and fifth units, two Asia-Pacific groups | the Asia-Pacific groups stay ask rows: the unit that tested them resolves to sony-kd-55x85j, this profile matches the EU fingerprint |
| tcl-43s5k | 32V5C, AU13 and Brazilian units; com.mediatek.MtkDemo | MtkDemo default-off (switched off and reboot-checked on AU13); the three [43S5K ONLY] rows are already ask rows or harmless preloads |
| tcl-55s470g | note that remote.service is the mic bridge (#1441); retitled launcher group | remote.service already an ask row and denylisted; title renamed with its old values |
| tcl-65c735 | 65C745 unit; its extra rows stay comments | header only |
| tcl-ak30a5 | second firmware (20250610); versionUpdateApp now kept on; header runs into the first group; warning that the kids and music apps feed the stock desktop's rows | versionUpdateApp no longer a row; the header (now read again) adds appmarket2 and versionUpdateApp to never-disable; ffeducation, ffkaraoke and tclmusic are ask rows, since the stock desktop stays Home on TCL China |
| tcl-g03-google-tv | StartandroidService now kept on (TCL updater); screensaver title extended | StartandroidService no longer a row; group 1 holds bi, logkit and feedback; title renamed with its old values |
| tcl-g05-google-tv | troubleshooting link | header only |
| tcl-g07-google-tv | fourth and fifth units; channelplus note | header only; channelplus stays denylisted (a TV input) |
| tcl-g09-google-tv | 55V6C unit and com.tcl.magiconnectfree | MagiConnect is the phone remote and mirroring app: a casting ask row |
| tcl-g10-google-tv | remote.service now kept on (#1441, the remote mic) | no longer a row; denylisted anyway |
| tcl-qm6k-google-tv | troubleshooting link | header only |
| thomson-32he5800 | second unit (France), com.tcl.waterfall.overseas tested with the real remote after a reboot | waterfall.overseas default-off (ads) |
| vidda-mt9653 | Undo line | header only; the profile stays the clean-up only |
| xiaomi-mi-box-s | third and fifth units; updater group moved before PatchWall | no package change; title renamed with its old values. The updater is a Software updates row (D22), so in Simple it stays on and can put PatchWall back (the header's note) |
| xiaomi-mi-box-s-2nd-gen | second unit (772); tvqs.overseas notes | header only; tvqs.overseas stays never-disable |
| xiaomi-mi-tv-4a | troubleshooting link | header only |
| xiaomi-mitv-aesp0 | third set (France), com.xiaomo.tv.milegal | milegal an ask row (neither ads nor telemetry); mitv.service stays denylisted |
| xiaomi-mitv-afmu0 | Undo line | header only |
| xiaomi-mitv-ansp0 | install note; mitv.service note (#1310) | header only |
| xiaomi-mitv-astp1 | install note; mitv.service note (#1310) | header only |
| xiaomi-mitv-mooq0 | Input key notes; Android TV 11 variant | header only; hotkey.dispatcher stays never-disable |
| xiaomi-mitv-moor2 | fourth TV on older firmware; optional second round as comments | header only |
| xiaomi-mitv-mssp1 | six groups of verified extras, reboot-checked | the telemetry uploaders and the partner auto-installer default-off; screensavers, TV Manager, Google apps and the media rows ask rows, TalkBack an accessibility row (denylisted); still under review for the brand sets |

## Brand sets

The full sets keep their rows; three notes in the Amazon set now come from amazon-fire-tv-afttiff43. Every
careful set is the 0.4.0 one, row for row: prismplus-q65-ultra-r10g leaves its new Privacy Sandbox and
feedbackconsent lines out, so it neither switches them off nor asks for them first.
`UP300-sure-sets.txt` has the per brand detail.

## Other lists

com.tcl.xian.StartandroidService is a Software updates row (D22) on ffalcon-65s585c-pro and tcl-q10g-pro,
from tcl-g03-google-tv's header at b06ab21 (TCL's updater reads the device number through it). Their lists
did not change.

com.tcl.ffeducation and com.tcl.ffkaraoke are ask rows on the same two lists, from tcl-ak30a5's header at
b06ab21 (the TCL China stock desktop, which stays Home, hands its kids and music rows to them).

## The now-kept packages

com.tcl.xian.StartandroidService, com.tcl.versionUpdateApp, com.google.android.tv.remote.service,
com.google.android.apps.tv.launcherx and com.google.android.tungsten.setupwraith are in no default-off
group of any profile and in no brand set.
