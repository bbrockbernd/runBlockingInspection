import com.example.simplerbdetection.RunBlockingInspection
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.AnimatedIcon.Default
import com.intellij.util.indexing.FileBasedIndex
import org.junit.jupiter.api.Test

class SimpleRunBlockingTest: LightJavaCodeInsightFixtureTestCase() {
    
    
    @Test
    fun testSimpleRunBlocking() {
        setUp()
        myFixture.testDataPath = "src/testDota"
        val psiFile = myFixture.configureByFile("SimpleTestClass.kt")
        
//        ModuleRootManager.getInstance(myFixture.module).modifiableModel.let { model -> MavenDependencyUtil.addFromMaven(model, "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0") }
        val analysisScope = AnalysisScope(psiFile)
        val toolWrapper = GlobalInspectionToolWrapper(RunBlockingInspection())
        val context = createGlobalContextForTool(analysisScope, psiFile.project, listOf(toolWrapper))
        runInEdtAndWait { InspectionTestUtil.runTool(toolWrapper, analysisScope, context) }
        InspectionTestUtil.compareToolResults(context, toolWrapper, true, "src/testDota")
        assert(true)
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
    
}