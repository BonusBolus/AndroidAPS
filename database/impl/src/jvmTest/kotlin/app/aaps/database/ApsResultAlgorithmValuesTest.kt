package app.aaps.database

import app.aaps.database.daos.ApsResultAlgorithmValues
import app.aaps.database.di.JvmAppDatabaseBuilder
import app.aaps.database.entities.APSResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The autosens ratio and target query of the graphs, against a real database file. Like
 * [ApsResultVariableSensTest]: the values are read with `json_extract`, so only a real SQLite can check the
 * field names, and one row with NaN must not make the query fail.
 */
class ApsResultAlgorithmValuesTest {

    private val directory = createTempDirectory("aaps-algovalues-test")
    private val database = JvmAppDatabaseBuilder().provideAppDatabase("$directory/aaps.db")

    @AfterTest
    fun tearDown() {
        database.close()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `reads the ratio and target of each result in the interval, oldest first`() = runTest {
        database.apsResultDao.insert(apsResult(NOW + FIVE_MIN, """{"sensitivityRatio":0.9,"targetBG":110.0}"""))
        database.apsResultDao.insert(apsResult(NOW, """{"bg":120.0,"sensitivityRatio":1.2,"targetBG":100.0,"variable_sens":48.0}"""))
        // Just outside the interval
        database.apsResultDao.insert(apsResult(NOW - 1, """{"sensitivityRatio":2.0,"targetBG":80.0}"""))
        database.apsResultDao.insert(apsResult(NOW + FIVE_MIN + 1, """{"sensitivityRatio":2.0,"targetBG":80.0}"""))

        assertThat(database.apsResultDao.getAlgorithmValues(NOW, NOW + FIVE_MIN)).containsExactly(
            ApsResultAlgorithmValues(NOW, 1.2, 100.0),
            ApsResultAlgorithmValues(NOW + FIVE_MIN, 0.9, 110.0)
        ).inOrder()
    }

    @Test
    fun `a missing value and NaN give null without failing the other rows`() = runTest {
        database.apsResultDao.insert(apsResult(NOW, """{"bg":120.0}"""))
        database.apsResultDao.insert(apsResult(NOW + FIVE_MIN, """{"sensitivityRatio":NaN,"targetBG":105.0}"""))

        assertThat(database.apsResultDao.getAlgorithmValues(NOW, NOW + FIVE_MIN)).containsExactly(
            ApsResultAlgorithmValues(NOW, null, null),
            ApsResultAlgorithmValues(NOW + FIVE_MIN, null, 105.0)
        ).inOrder()
    }

    private fun apsResult(timestamp: Long, resultJson: String) = APSResult(
        timestamp = timestamp,
        algorithm = APSResult.Algorithm.SMB,
        glucoseStatusJson = null,
        currentTempJson = null,
        iobDataJson = null,
        profileJson = null,
        autosensDataJson = null,
        mealDataJson = null,
        resultJson = resultJson
    )

    private companion object {

        const val NOW = 1_800_000_000_000L
        const val FIVE_MIN = 5 * 60 * 1000L
    }
}
