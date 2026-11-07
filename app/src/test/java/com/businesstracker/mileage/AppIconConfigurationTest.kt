package com.businesstracker.mileage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class AppIconConfigurationTest {
    @Test
    fun launcherAndRoundIcon_useSuppliedArtwork() {
        val manifest = projectFile("src/main/AndroidManifest.xml")
        val content = manifest.readText()

        assertTrue(content.contains("android:icon=\"@drawable/app_icon_artwork\""))
        assertTrue(content.contains("android:roundIcon=\"@drawable/app_icon_artwork\""))
    }

    @Test
    fun suppliedArtwork_isSquareAndHighResolution() {
        val artwork = projectFile("src/main/res/drawable-nodpi/app_icon_artwork.jpg")
        val image = ImageIO.read(artwork)

        assertEquals(1280, image.width)
        assertEquals(1280, image.height)
        assertTrue("Launcher artwork must not be empty", artwork.length() > 10_000L)
    }

    private fun projectFile(relativePath: String): File = File(relativePath).let {
        if (it.exists()) it else File("app/$relativePath")
    }.also {
        assertTrue("Expected project file: ${it.path}", it.exists())
    }
}
