package com.bizzeh.bruce.settings

import androidx.annotation.RawRes
import com.bizzeh.bruce.R

/** Software shipped inside Bruce, and the licence each is distributed under. */
data class LicensedComponent(val name: String, val licence: String, @RawRes val text: Int)

object OpenSourceLicences {
    val components = listOf(
        LicensedComponent("Bruce", "GNU General Public License v3.0 or later", R.raw.licence_gpl_3_0),
        LicensedComponent("llama.cpp and ggml", "MIT", R.raw.licence_mit_ggml),
        LicensedComponent("Vulkan-Headers", "MIT", R.raw.licence_mit_vulkan_headers),
        LicensedComponent("SPIRV-Headers", "MIT", R.raw.licence_spirv_headers),
        LicensedComponent("OpenCL-Headers", "Apache License 2.0", R.raw.licence_apache_2_0),
        LicensedComponent("LLVM libc++ and OpenMP runtime", "Apache License 2.0 with LLVM Exceptions", R.raw.licence_llvm),
        LicensedComponent("Android Jetpack (AndroidX, Compose, Material 3, DataStore)", "Apache License 2.0", R.raw.licence_apache_2_0),
        LicensedComponent("Kotlin standard library and kotlinx.coroutines", "Apache License 2.0", R.raw.licence_apache_2_0),
        LicensedComponent("commonmark-java", "BSD 2-Clause", R.raw.licence_bsd_2_commonmark),
        LicensedComponent("stb_image, xxHash and rotate-bits (in llama.cpp's image library)", "MIT and BSD 2-Clause", R.raw.licence_mtmd_vendored),
        LicensedComponent("PdfBox-Android (Apache PDFBox), with Adobe glyph lists and CMaps", "Apache License 2.0 and others, see text", R.raw.licence_pdfbox_android),
        LicensedComponent("Material Icons", "Apache License 2.0", R.raw.licence_apache_2_0),
    )
}
