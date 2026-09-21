package com.robin.tools.feature.storage.presentation

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robin.tools.feature.storage.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * 单位资源在真实 Android 资源系统下的验证。
 *
 * 纯逻辑由 JVM 的 `MetricFormatTest` 覆盖，但"资源 id 与占位符能否正确替换"
 * 只有真实的 `Resources` 才能验证——尤其是单位在不同语言下写法不同这一点。
 *
 * 各语言的**完整性**由 lint 的 `MissingTranslation` 保证，这里不重复检查。
 */
@RunWith(AndroidJUnit4::class)
class MetricFormatResourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyLocaleSubstitutesTheNumberIntoEveryUnit() {
        SUPPORTED_LOCALES.forEach { locale ->
            val localized = localizedContext(locale)
            val number = MetricFormat.scaledNumber(1.5, locale)

            UNIT_STRING_IDS.forEach { id ->
                val text = localized.getString(id, number)
                assertTrue(
                    "$locale: ${localized.getString(id, number)} should contain \"$number\"",
                    text.contains(number)
                )
                // 占位符没被替换掉的话，文本里会留下 %1$s。
                assertTrue("$locale: $text still has a placeholder", !text.contains("%1\$s"))
            }
        }
    }

    @Test
    fun frenchUsesOctetsInsteadOfBytes() {
        // 这正是"单位必须进资源"的具体理由：法语用 Mo/Go，不是 MB/GB。
        val french = localizedContext(Locale.FRANCE)

        assertEquals("1,5 Mo", french.getString(R.string.storage_unit_megabytes, "1,5"))
        assertEquals("1,5 Go", french.getString(R.string.storage_unit_gigabytes, "1,5"))
        assertEquals("1,5 Mo/s", french.getString(R.string.storage_unit_megabytes_per_second, "1,5"))
    }

    @Test
    fun frenchUsesACommaAsTheDecimalSeparator() {
        // 数字交给 Locale 格式化，单位交给资源，两者结合才能得到当地写法。
        val french = localizedContext(Locale.FRANCE)
        val number = MetricFormat.scaledNumber(1.5, Locale.FRANCE)

        assertEquals("1,5", number)
        assertEquals("1,5 Mo", french.getString(R.string.storage_unit_megabytes, number))
    }

    @Test
    fun everyLocaleHasItsOwnTranslationForTheCaveat() {
        // 口径注释是本功能的诚信底线，六个语言都要有真正翻译过的版本，
        // 不能整段回退到英文。
        val english = localizedContext(Locale.US).getString(R.string.storage_read_caveat)

        SUPPORTED_LOCALES.filter { it != Locale.US }.forEach { locale ->
            val localized = localizedContext(locale).getString(R.string.storage_read_caveat)
            assertTrue("$locale still shows the English caveat", localized != english)
            assertTrue("$locale caveat is suspiciously short", localized.length > MIN_CAVEAT_LENGTH)
        }
    }

    private fun localizedContext(locale: Locale): Context {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(configuration)
    }

    private companion object {
        const val MIN_CAVEAT_LENGTH = 30

        val SUPPORTED_LOCALES = listOf(
            Locale.US,
            Locale.SIMPLIFIED_CHINESE,
            Locale.FRANCE,
            Locale("es", "ES"),
            Locale.ITALY,
            Locale("pt", "PT")
        )

        val UNIT_STRING_IDS = listOf(
            R.string.storage_unit_bytes,
            R.string.storage_unit_kilobytes,
            R.string.storage_unit_megabytes,
            R.string.storage_unit_gigabytes,
            R.string.storage_unit_bytes_per_second,
            R.string.storage_unit_kilobytes_per_second,
            R.string.storage_unit_megabytes_per_second,
            R.string.storage_unit_gigabytes_per_second,
            R.string.storage_unit_microseconds,
            R.string.storage_unit_milliseconds,
            R.string.storage_unit_seconds
        )
    }
}
