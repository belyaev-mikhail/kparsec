package ru.spbstu.kparsec

/**
 * The basic parser interface.
 *
 * @param T the type of input tokens (Char for simple cases)
 * @param R the type of result of parsing
 */
interface Parser<T, out R> {
    /**
     * Perform parsing on [input]
     * @see ParseResult
     * @return the result of parsing
     */
    operator fun invoke(input: Source<T>): ParseResult<T, R>

    val description: String
}

/**
 * Parse a string
 * @see StringInput
 */
fun<T> Parser<Char, T>.parse(string: CharSequence): ParseResult<Char, T> = this(Source("<string>", StringInput(string)))

/**
 * Parse a list of tokens
 * @see ListInput
 */
fun<T, E> Parser<T, E>.parse(data: List<T>): ParseResult<T, E> = this(Source("<list data>", ListInput(data)))

/**
 * Parse an array of tokens
 * @see ListInput
 */
fun<T, E> Parser<T, E>.parse(data: Array<T>): ParseResult<T, E> = this(Source("<list data>", ListInput(data.asList())))
