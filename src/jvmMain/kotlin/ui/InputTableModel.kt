package ui

import java.util.ArrayList

import javax.swing.JProgressBar
import javax.swing.table.DefaultTableModel

class InputTableModel : DefaultTableModel() {
    private val COLUMN_NAMES = arrayOf("No", "Type", "File", "Progress")
    private val COLUMN_CLASSES = arrayOf<Class<*>>(Int::class.java, String::class.java, String::class.java, JProgressBar::class.java)
    private val progresses = ArrayList<JProgressBar>()

    override fun getColumnCount(): Int {
        return 4
    }

    override fun getColumnName(column: Int): String {
        return COLUMN_NAMES[column]
    }

    override fun getValueAt(row: Int, column: Int): Any {
        if (column == 0) {
            return row + 1
        }
        if (column == 3) {
            if (row >= progresses.size) {
                progresses.add(JProgressBar())
            }
            val bar = progresses[row]
            bar.minimum = 0
            bar.maximum = 100
            return bar
        }
        return super.getValueAt(row, column)
    }

    override fun getColumnClass(column: Int): Class<*> {
        return COLUMN_CLASSES[column]
    }

    override fun fireTableRowsDeleted(firstRow: Int, lastRow: Int) {
        for (row in firstRow..lastRow) {
            progresses.removeAt(row)
        }
        super.fireTableRowsDeleted(firstRow, lastRow)
    }

    override fun isCellEditable(row: Int, column: Int): Boolean {
        return false
    }

    companion object {

        private val serialVersionUID = 1L
    }
}
