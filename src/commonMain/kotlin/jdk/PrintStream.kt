package jdk

class PrintStream {
    fun printf(template: String, vararg args: Any) {
        print(template)
    }

    fun println(s: String) {
        kotlin.io.println(s)
    }

}