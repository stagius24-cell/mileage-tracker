package com.businesstracker.mileage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupConfigurationTest {

    @Test
    fun manifest_referencesBackupAndDataExtractionRules() {
        val manifestFile = File("src/main/AndroidManifest.xml").let {
            if (it.exists()) it else File("app/src/main/AndroidManifest.xml")
        }
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val content = manifestFile.readText()

        assertTrue(
            "AndroidManifest.xml must reference dataExtractionRules",
            content.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\"")
        )
        assertTrue(
            "AndroidManifest.xml must reference fullBackupContent",
            content.contains("android:fullBackupContent=\"@xml/backup_rules\"")
        )
        assertTrue(
            "AndroidManifest.xml should retain allowBackup=true for general app data",
            content.contains("android:allowBackup=\"true\"")
        )
    }

    @Test
    fun dataExtractionRules_excludesSecureCredentialsFromCloudAndTransfer() {
        val rulesFile = File("src/main/res/xml/data_extraction_rules.xml").let {
            if (it.exists()) it else File("app/src/main/res/xml/data_extraction_rules.xml")
        }
        assertTrue("data_extraction_rules.xml must exist", rulesFile.exists())
        val content = rulesFile.readText()

        assertTrue(
            "data_extraction_rules must define cloud-backup exclusion",
            content.contains("<cloud-backup>") && content.contains("path=\"mileage_secure_credentials.xml\"")
        )
        assertTrue(
            "data_extraction_rules must define device-transfer exclusion",
            content.contains("<device-transfer>") && content.contains("path=\"mileage_secure_credentials.xml\"")
        )
    }

    @Test
    fun backupRules_excludesSecureCredentialsFromFullBackup() {
        val rulesFile = File("src/main/res/xml/backup_rules.xml").let {
            if (it.exists()) it else File("app/src/main/res/xml/backup_rules.xml")
        }
        assertTrue("backup_rules.xml must exist", rulesFile.exists())
        val content = rulesFile.readText()

        assertTrue(
            "backup_rules must exclude mileage_secure_credentials.xml from full-backup-content",
            content.contains("<full-backup-content>") && content.contains("path=\"mileage_secure_credentials.xml\"")
        )
    }
}
