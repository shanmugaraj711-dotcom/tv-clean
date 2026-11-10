package ai.promptstudio.tvclean.core.spec

import kotlin.math.roundToInt

// What the Look editor offers on a given TV: by the home screen's version (launcher 2.7 is
// versionCode 8, the first to read the look2 keys; launcher 3.0 is versionCode 9, the first to read
// the LookExtras keys, and the bundled one) and by its memory (spec 6.5, 8.1).
object LookGates {
    const val FIRST_27_VERSION_CODE = 8L
    const val FIRST_30_VERSION_CODE = 9L
    const val LOW_RAM_MB = 1536
    const val LIFT_MIN_RAM_MB = 2048

    // Styles offered: below versionCode 8 only CLASSIC; 8 and up, or not known (the bundled launcher), all five.
    fun styles(versionCode: Long?): List<StyleId> = if (reads27(versionCode)) StyleId.entries.toList() else listOf(StyleId.CLASSIC)

    // The TV's home screen reads the 2.7 keys (names and the 2.7 rows of More options).
    fun reads27(versionCode: Long?): Boolean = versionCode == null || versionCode >= FIRST_27_VERSION_CODE

    // The TV's home screen reads the 3.0 keys (LookExtras), with the same "unknown means bundled"
    // rule: a versionCode not known is the launcher this app bundles, 3.0 from 0.4.5 on.
    fun reads30(versionCode: Long?): Boolean = versionCode == null || versionCode >= FIRST_30_VERSION_CODE

    // Spec 8.1: QUICK at 1536 MB or less, otherwise LIVELY (memory not known: LIVELY).
    fun defaultMovement(ramMb: Int?): Movement = if (ramMb != null && ramMb <= LOW_RAM_MB) Movement.QUICK else Movement.LIVELY

    // Spec 6.5: Lift and zoom shows at 2048 MB or more, or memory not known, or when it is already on.
    fun liftShown(ramMb: Int?, liftOn: Boolean): Boolean = liftOn || ramMb == null || ramMb >= LIFT_MIN_RAM_MB

    // The memory rounded to the nearest half GB, for "1.5 GB" (1024 gives 1.0, 1536 gives 1.5).
    fun halfGb(ramMb: Int): Double = (ramMb / 512.0).roundToInt() / 2.0
}
