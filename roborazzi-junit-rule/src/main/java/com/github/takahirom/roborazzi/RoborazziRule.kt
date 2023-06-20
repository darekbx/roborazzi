package com.github.takahirom.roborazzi

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.espresso.ViewInteraction
import java.io.File
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement

class RoborazziRule private constructor(
  private val captureRoot: CaptureRoot,
  private val options: Options = Options()
) : TestWatcher() {
  /**
   * If you add this annotation to the test, the test will be ignored by roborazzi
   */
  annotation class Ignore

  internal sealed interface CaptureRoot {
    class Compose(
      val composeRule: AndroidComposeTestRule<*, *>,
      val semanticsNodeInteraction: SemanticsNodeInteraction
    ) : CaptureRoot

    class View(val viewInteraction: ViewInteraction) : CaptureRoot
  }

  data class Options(
    val captureType: CaptureType = CaptureType.LastImage,
    /**
     * capture only when the test fail
     */
    val onlyFail: Boolean = false,
    /**
     * output directory path
     */
    val outputDirectoryPath: String = DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH,
    val outputFileProvider: (
      description: Description,
      folder: File,
      fileExtension: String
    ) -> File = { description, folder, fileExtension ->
      File(
        folder.absolutePath,
        DefaultFileNameGenerator.generate(description) + "." + fileExtension
      )
    },
    val roborazziOptions: RoborazziOptions = RoborazziOptions(),
  )

  enum class CaptureType {
    /**
     * Do not generate images. Just provide the image path and run the test.
     */
    @ExperimentalRoborazziApi
    None,

    /**
     * Generate last images for each test
     */
    LastImage,

    /**
     * Generate images for Each layout change like TestClass_method_0.png for each test
     */
    AllImage,

    /**
     * Generate gif images for each test
     */
    Gif
  }


  constructor(
    captureRoot: ViewInteraction,
    options: Options = Options()
  ) : this(
    captureRoot = CaptureRoot.View(captureRoot),
    options = options
  )

  constructor(
    composeRule: AndroidComposeTestRule<*, *>,
    captureRoot: SemanticsNodeInteraction,
    options: Options = Options()
  ) : this(
    captureRoot = CaptureRoot.Compose(composeRule, captureRoot),
    options = options
  )

  override fun failed(e: Throwable?, description: Description?) {
    super.failed(e, description)
  }

  @OptIn(ExperimentalRoborazziApi::class)
  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        val evaluate = {
          try {
            provideRoborazziContext().setRuleOverrideOutputDirectory(options.outputDirectoryPath)
            base.evaluate()
          } catch (e: Exception) {
            throw e
          } finally {
            provideRoborazziContext().clearRuleOverrideOutputDirectory()
          }
        }
        val captureType = options.captureType
        if (!roborazziEnabled()) {
          evaluate()
          return
        }
        if (!roborazziRecordingEnabled() && options.captureType == CaptureType.Gif) {
          // currently, gif compare is not supported
          evaluate()
          return
        }
        if (description.annotations.filterIsInstance<Ignore>().isNotEmpty()) return evaluate()
        val folder = File(options.outputDirectoryPath)
        if (!folder.exists()) {
          folder.mkdirs()
        }

        when (captureType) {
          CaptureType.AllImage, CaptureType.Gif -> {
            val result = when (captureRoot) {
              is CaptureRoot.Compose -> captureRoot.semanticsNodeInteraction.captureComposeNode(
                composeRule = captureRoot.composeRule,
                roborazziOptions = options.roborazziOptions,
                block = evaluate
              )

              is CaptureRoot.View -> captureRoot.viewInteraction.captureAndroidView(
                roborazziOptions = options.roborazziOptions,
                block = evaluate
              )
            }
            if (!options.onlyFail || result.result.isFailure) {
              if (captureType == CaptureType.AllImage) {
                result.saveAllImage {
                  options.outputFileProvider(description, folder, "png")
                }
              } else {
                val file = options.outputFileProvider(description, folder, "gif")
                result.saveGif(file)
              }
            }
            result.clear()
            result.result.exceptionOrNull()?.let {
              throw it
            }

          }

          CaptureType.None -> {
            evaluate()
          }

          CaptureType.LastImage -> {
            val result = runCatching {
              evaluate()
            }
            if (!options.onlyFail || result.isFailure) {
              val outputFile = options.outputFileProvider(description, folder, "png")
              when (captureRoot) {
                is CaptureRoot.Compose -> captureRoot.semanticsNodeInteraction.captureRoboImage(
                  outputFile
                )

                is CaptureRoot.View -> captureRoot.viewInteraction.captureRoboImage(outputFile)
              }
            }
            result.exceptionOrNull()?.let {
              throw it
            }
          }
        }
      }
    }
  }
}