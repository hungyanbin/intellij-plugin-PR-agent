package com.github.hungyanbin.pragent.toolWindow

import com.github.hungyanbin.pragent.repository.SecretRepository
import com.github.hungyanbin.pragent.utils.runOnUI
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.w3c.dom.Element
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.StringReader
import javax.imageio.ImageIO
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JPanel
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

data class ImageInfo(
    val url: String,
    val alt: String,
    val width: Int?,
    val height: Int?
)

class ImageListPanel : JBPanel<JBPanel<*>>() {

    private val imageListContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }

    private val scrollPane = JBScrollPane(imageListContainer).apply {
        verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val httpClient = HttpClient(CIO)
    private val secretRepository = SecretRepository()

    init {
        layout = BorderLayout()
        add(scrollPane, BorderLayout.CENTER)
    }

    fun updateImages(prNotesText: String) {
        // Clear existing images
        imageListContainer.removeAll()

        // Parse images from PR notes
        val images = parseImages(prNotesText)

        if (images.isEmpty()) {
            val noImagesLabel = JBLabel("No images found in PR notes").apply {
                border = javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10)
            }
            imageListContainer.add(noImagesLabel)
        } else {
            // Load and display each image
            images.forEach { imageInfo ->
                val imagePanel = createImagePanel(imageInfo)
                imageListContainer.add(imagePanel)
            }
        }

        imageListContainer.revalidate()
        imageListContainer.repaint()
    }

    private fun parseImages(text: String): List<ImageInfo> {
        val images = mutableListOf<ImageInfo>()

        // Find all img tags in the text
        val imgTagRegex = Regex("""<img\s+[^>]*?/?>""", RegexOption.IGNORE_CASE)
        val matches = imgTagRegex.findAll(text)

        matches.forEach { match ->
            val imgTag = match.value
            try {
                // Wrap in a root element to make it valid XML
                val wrappedXml = "<root>$imgTag</root>"
                val factory = DocumentBuilderFactory.newInstance()
                val builder = factory.newDocumentBuilder()
                val doc = builder.parse(InputSource(StringReader(wrappedXml)))

                val imgElements = doc.getElementsByTagName("img")
                if (imgElements.length > 0) {
                    val img = imgElements.item(0) as Element
                    val src = img.getAttribute("src")
                    if (src.isNotEmpty()) {
                        val alt = img.getAttribute("alt").ifEmpty { "Image" }
                        val width = img.getAttribute("width").toIntOrNull()
                        val height = img.getAttribute("height").toIntOrNull()

                        images.add(ImageInfo(src, alt, width, height))
                    }
                }
            } catch (e: Exception) {
                // Skip invalid img tags
            }
        }

        // Also support markdown image syntax: ![alt](url)
        val markdownImgRegex = Regex("""!\[([^\]]*)\]\(([^)]+)\)""")
        markdownImgRegex.findAll(text).forEach { match ->
            val alt = match.groupValues[1].ifEmpty { "Image" }
            val url = match.groupValues[2]
            images.add(ImageInfo(url, alt, null, null))
        }

        return images
    }

    private fun createImagePanel(imageInfo: ImageInfo): JPanel {
        val panel = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()
            border = javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10),
                javax.swing.BorderFactory.createLineBorder(java.awt.Color.GRAY, 1)
            )
            // Don't set fixed size, let it adjust to content
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }

        // Add label with alt text
        val altLabel = JBLabel(imageInfo.alt).apply {
            border = javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5)
        }
        panel.add(altLabel, BorderLayout.NORTH)

        // Add loading label
        println("Loading...")

        // Load image asynchronously
        coroutineScope.launch {
            try {
                val image = loadImage(imageInfo.url)
                runOnUI {
                    if (image != null) {
                        // Scale image to 1/4 of typical screen width (e.g., 400px max)
                        val maxImageWidth = 400
                        val maxImageHeight = 300
                        val scaledImage = scaleImage(image, maxImageWidth, maxImageHeight)
                        val imageLabel = JBLabel(ImageIcon(scaledImage)).apply {
                            horizontalAlignment = JBLabel.LEFT
                        }
                        panel.add(imageLabel, BorderLayout.CENTER)

                        // Update panel size based on actual image size
                        val icon = imageLabel.icon
                        panel.preferredSize = Dimension(
                            maxOf(icon.iconWidth + 20, 200),
                            icon.iconHeight + altLabel.preferredSize.height + 20
                        )
                    } else {
                        println("Failed to load image, image is null")
                    }

                    panel.revalidate()
                    panel.repaint()
                    imageListContainer.revalidate()
                    imageListContainer.repaint()
                }
            } catch (e: Exception) {
                runOnUI {
                    println("Error: ${e.message}")
                    panel.revalidate()
                    panel.repaint()
                }
            }
        }

        return panel
    }

    private suspend fun loadImage(urlString: String): BufferedImage? = withContext(Dispatchers.IO) {
        try {
            println("Start to load image: ${urlString}")
            val githubPat = secretRepository.getGithubPat()
            val response: HttpResponse = httpClient.get(urlString) {
                // Add GitHub PAT if available and URL is from GitHub
                if (!githubPat.isNullOrEmpty() && urlString.contains("github.com")) {
                    headers {
                        append(HttpHeaders.Authorization, "token $githubPat")
                    }
                }
            }
            val bytes = response.readBytes()
            val inputStream = ByteArrayInputStream(bytes)
            val image = ImageIO.read(inputStream)
            inputStream.close()
            image
        } catch (e: Exception) {
            println("Load image error: ${e.message}")
            null
        }
    }

    private fun scaleImage(image: BufferedImage, maxWidth: Int, maxHeight: Int): Image {
        val width = image.width
        val height = image.height

        if (width <= maxWidth && height <= maxHeight) {
            return image
        }

        val widthRatio = maxWidth.toDouble() / width
        val heightRatio = maxHeight.toDouble() / height
        val ratio = minOf(widthRatio, heightRatio)

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)
    }

    fun cleanup() {
        coroutineScope.cancel()
        httpClient.close()
    }
}