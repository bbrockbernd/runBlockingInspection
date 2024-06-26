import com.example.runBlockingInspection.RunBlockingInspection
import com.example.runBlockingInspection.services.DetectRunBlockingService
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
    fun runIndividualTest(): Collection<DynamicTest> {
        val testIndex = 1
        return listOf(runTest(myTests.tests[testIndex]))
    }
    
    
    @TestFactory
    fun runBlockingTests(): Collection<DynamicTest> {
        return myTests.tests.map(::runTest)
    }
    
    fun runTest(test: Test): DynamicTest {
        return dynamicTest(test.name) {
            val psiFiles = test.inputFiles.map { inputFile -> psiFileMap[inputFile]!!.virtualFile}
            val analysisScope = AnalysisScope(myFixture.project, psiFiles)
            val toolWrapper = GlobalInspectionToolWrapper(RunBlockingInspection())
            val context = createGlobalContextForTool(analysisScope, myFixture.project, listOf(toolWrapper))
            (toolWrapper.tool as RunBlockingInspection).explorationLevel =
                when (test.strictness) {
                    "strict" -> RunBlockingInspection.ExplorationLevel.STRICT
                    "all" -> RunBlockingInspection.ExplorationLevel.ALL
                    else -> RunBlockingInspection.ExplorationLevel.DECLARATION
                }
            
            runInEdtAndWait {
                InspectionTestUtil.runTool(toolWrapper, analysisScope, context)
                val results = myFixture.project.service<DetectRunBlockingService>().checkAllRunBlockings()
                assertResults(results, test)
            }
        }
    }
    
    private fun assertResults(foundResults: Collection<DetectRunBlockingService.RunBlockingProblem>, test: Test) {
        // Assert equal amount of results
        Assertions.assertEquals(test.results.size, foundResults.size)
        // Verify each result
        test.results.forEach { expectation ->
            val checkOffsets = expectation.trace.last().offset != -1
            //Find corresponding runBlocking, with or without offset
            val expectedRBUrl = "temp:///src/${expectation.trace.last().file}#${expectation.trace.last().offset}"
            val foundTrace = foundResults.firstOrNull { 
                if (checkOffsets) {
                    it.stacTrace.last().url == expectedRBUrl
                } else {
                    it.stacTrace.last().url.split("#")[0] == expectedRBUrl.split("#")[0]
                }
            }
            
            //Verify runBlocking found
            Assertions.assertNotNull(foundTrace, "Following runBlocking not detected: $expectedRBUrl")
            //Verify files
            Assertions.assertArrayEquals(expectation.trace.map {"temp:///src/${it.file}"}.toTypedArray(), foundTrace!!.stacTrace.map {it.url.split("#")[0]}.toTypedArray())
            //Verify offsets
            if (checkOffsets) Assertions.assertArrayEquals(expectation.trace.map {it.offset.toString()}.toTypedArray(), foundTrace!!.stacTrace.map {it.url.split("#")[1]}.toTypedArray())
            //Verify fqNames
            Assertions.assertArrayEquals(expectation.trace.map {it.fqName}.toTypedArray(), foundTrace.stacTrace.map {it.fgName}.toTypedArray())
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
    data class Trace(val fqName: String, val file: String, val offset: Int = -1)

    @Serializable
    data class Result(val trace: List<Trace>)

    @Serializable
    data class Test(val name: String, val inputFiles: List<String>, val results: List<Result>, val strictness: String = "declared")

    @Serializable
    data class Tests(val tests: List<Test>)
}