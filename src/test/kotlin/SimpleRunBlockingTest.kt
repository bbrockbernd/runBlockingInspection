import com.example.simplerbdetection.RunBlockingInspection
import com.example.simplerbdetection.Services.DetectRunBlockingService
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.psi.PsiFile
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.util.collectionUtils.concat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DynamicTest.dynamicTest
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleRunBlockingTest: LightJavaCodeInsightFixtureTestCase() {

    
    private val psiFileMap = mutableMapOf<String, PsiFile>()
    private lateinit var myTests: Tests
    
    @BeforeAll
    fun initialize() {
        setUp()
        myFixture.testDataPath = "src/testDota"
        val jsonString = File("src/testDota/tests.json").readText()
        myTests = Json.decodeFromString<Tests>(jsonString)
        val aggregatedInputFiles = myTests.tests.fold(mutableListOf<String>()) { acc, test -> acc.concat(test.inputFiles)!!.toMutableList() }
        aggregatedInputFiles.forEach { input ->
            val psiFile = myFixture.configureByFile(input)
            psiFileMap[input] = psiFile
        }
    }
    
    @TestFactory
    fun runBlockingTests(): Collection<DynamicTest> {
        return myTests.tests.map { test ->
            dynamicTest(test.name) {
                val psiFiles = test.inputFiles.map { inputFile -> psiFileMap[inputFile]!!.virtualFile}
                val analysisScope = AnalysisScope(myFixture.project, psiFiles)
                val toolWrapper = GlobalInspectionToolWrapper(RunBlockingInspection())
                val context = createGlobalContextForTool(analysisScope, myFixture.project, listOf(toolWrapper))
                runInEdtAndWait { InspectionTestUtil.runTool(toolWrapper, analysisScope, context) }
                runInEdtAndWait { 
                    val results = myFixture.project.service<DetectRunBlockingService>().wholeProject() 
                    assertResults(results, test)
                }
            }
        }
    }
    
    private fun assertResults(foundResults: Collection<DetectRunBlockingService.RunBlockingProblem>, test: Test) {
        Assertions.assertEquals(test.results.size, foundResults.size)
        test.results.forEach { expectation ->
            val expectedRBUrl = "temp:///src/${expectation.trace.last().file}#${expectation.trace.last().offset}"
            val foundTrace = foundResults.first { it.stacTrace.last().second == expectedRBUrl }
            Assertions.assertNotNull(foundTrace)
            Assertions.assertArrayEquals(expectation.trace.map {"temp:///src/${it.file}#${it.offset}"}.toTypedArray(), foundTrace.stacTrace.map {it.second}.toTypedArray())
            Assertions.assertArrayEquals(expectation.trace.map {it.fqName}.toTypedArray(), foundTrace.stacTrace.map {it.first}.toTypedArray())
        }
    }
    
    
    override fun getTestDataPath() = "src/testDota"

    override fun getProjectDescriptor(): DefaultLightProjectDescriptor {
        return object : DefaultLightProjectDescriptor() {
            override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
                super.configureModule(module, model, contentEntry)
                MavenDependencyUtil.addFromMaven(model, "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            }
        }
    }

    @Serializable
    data class Trace(val fqName: String, val file: String, val offset: Int)

    @Serializable
    data class Result(val trace: List<Trace>)

    @Serializable
    data class Test(val name: String, val inputFiles: List<String>, val results: List<Result>)

    @Serializable
    data class Tests(val tests: List<Test>)
}