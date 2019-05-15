package ui

import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints

import javax.swing.JComponent

/**
 * @param collection
 */
class Picture : JComponent() {

    private var composerImage: Image? = null

    init {
        isOpaque = true
        preferredSize = Dimension(200, 200)
    }

    fun setComposerImage(image: Image) {
        composerImage = image
    }

    override fun paint(g: Graphics) {
        g.color = background
        g.fillRect(0, 0, width, height)
        if (composerImage !=
                null) {
            var picWidth = composerImage!!.getWidth(null)
            var picHeight = composerImage!!.getHeight(null)

            val availableWidth = width
            val availableHeight = height

            /* figure out which dimension limits scaling first */
            val scaleW = availableWidth.toFloat() / picWidth
            val scaleH = availableHeight.toFloat() / picHeight

            val safeScale = if (scaleW > scaleH) scaleH else scaleW
            picWidth *= safeScale.toInt()
            picHeight *= safeScale.toInt()

            (g as Graphics2D).setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.drawImage(composerImage, (availableWidth - picWidth) / 2,
                    (availableHeight - picHeight) / 2, picWidth, picHeight, null)
        }
    }

    override fun update(g: Graphics) {
        paint(g)
    }

    companion object {

        private val serialVersionUID = 1L
    }
}