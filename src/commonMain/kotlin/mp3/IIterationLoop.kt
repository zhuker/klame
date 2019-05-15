package mp3

/**
 * Global Type Definitions
 *
 * @author Ken
 */
interface IIterationLoop {
    fun iteration_loop(gfp: LameGlobalFlags, pe: Array<FloatArray>,
                       ms_ratio: FloatArray, ratio: Array<Array<III_psy_ratio>>)
}