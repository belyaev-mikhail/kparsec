package ru.spbstu.kparsec.parsers

import ru.spbstu.kparsec.*

const val DEFAULT_PRIORITY = 7
interface Assoc {
    val index: Int
    companion object {
        val LEFT = BinaryAssoc.LEFT
        val RIGHT = BinaryAssoc.RIGHT
        val NONE = BinaryAssoc.NONE
        val PREFIX = UnaryAssoc.PREFIX
        val POSTFIX = UnaryAssoc.POSTFIX
    }
}

enum class BinaryAssoc(override val index: Int): Assoc { LEFT(0), RIGHT(1), NONE(2) }
enum class UnaryAssoc(override val index: Int): Assoc { PREFIX(3), POSTFIX(4) }

private typealias Mapping<Base, Op> = (Base, Op, Base) -> Base
private data class SortedKey(val priority: Int, val assoc: Assoc): Comparable<SortedKey> {
    override fun compareTo(other: SortedKey): Int {
        val priComp = priority.compareTo(other.priority)
        if(priComp != 0) return priComp
        return assoc.index.compareTo(other.assoc.index)
    }
}

private data class Entry<T, E, K>(
        val op: Parser<T, K>,
        val mapping: Mapping<E, K>
): Parser<T, (E, E) -> E> {
    override fun invoke(input: Source<T>): ParseResult<T, (E, E) -> E> =
            op(input).map { op -> { a: E, b: E -> mapping(a, op, b) }}

    override val description: String
        get() = "operator(${op.description})"
}

@DslMarker
annotation class KParsecOperatorTable

@KParsecOperatorTable
class OperatorTableContext<T, Base>(val base: Parser<T, Base>) {
    private val map: MutableMap<SortedKey, MutableList<Entry<T, Base, *>>> = mutableMapOf()

    private fun Parser<T, (Base, Base) -> Base>.apply(element: Parser<T, Base>): Parser<T, Base> =
            zip(element, this, element) { l, f, r -> f(l, r) }

    operator fun<K> Parser<T, K>.invoke(priority: Int = DEFAULT_PRIORITY,
                                        assoc: UnaryAssoc,
                                        mapping: (Base) -> Base) {
        map.getOrPut(SortedKey(priority, assoc)){ mutableListOf() } += Entry( this){ a, _, _ -> mapping(a) }
    }

    operator fun<K> Parser<T, K>.invoke(priority: Int = DEFAULT_PRIORITY,
                                        assoc: UnaryAssoc,
                                        mapping: (Base, K) -> Base) {
        map.getOrPut(SortedKey(priority, assoc)){ mutableListOf() } += Entry( this){ a, op, _ -> mapping(a, op) }
    }

    operator fun<K> Parser<T, K>.invoke(priority: Int = DEFAULT_PRIORITY,
                                        assoc: BinaryAssoc = Assoc.LEFT,
                                        mapping: (Base, K, Base) -> Base) {
        map.getOrPut(SortedKey(priority, assoc)){ mutableListOf() } += Entry( this, mapping)
    }

    operator fun<K> Parser<T, K>.invoke(priority: Int = DEFAULT_PRIORITY,
                                        assoc: BinaryAssoc = Assoc.LEFT,
                                        mapping: (Base, Base) -> Base) {
        map.getOrPut(SortedKey(priority, assoc)){ mutableListOf() } += Entry( this){ a, _, b -> mapping(a, b) }
    }

    internal fun build(): Parser<T, Base> {
        var currentElement: Parser<T, Base> = base

        val sortedKeys = map.keys.sortedByDescending{ it }
        for(key in sortedKeys) {
            val op =
                    oneOfCollection(map[key] as Iterable<Parser<T, (Base, Base) -> Base>>)
            currentElement = when(key.assoc) {
                Assoc.LEFT -> zip(currentElement, zip(op, currentElement).many()){ first, rest ->
                    rest.fold(first){ l, (op, r) -> op(l, r) }
                }
                Assoc.RIGHT -> zip(zip(currentElement, op).many(), currentElement){ rest, last ->
                    rest.foldRight(last){ (l, op), r -> op(l, r) }
                }
                Assoc.NONE -> zip(currentElement, zip(op, currentElement).orNot()) { l, pair ->
                    if(pair == null) l
                    else pair.first(l, pair.second)
                }

                Assoc.PREFIX -> zip(op.many(), currentElement) { f, r ->
                    f.foldRight(r) { op, rhv -> op(rhv, rhv) }
                }
                Assoc.POSTFIX -> zip(currentElement, op.many()) { r, f ->
                    f.fold(r) { rhv, op -> op(rhv, rhv) }
                }
                else -> /* =( */ error("Unknown associativity type: ${key.assoc}")
            }
        }
        return currentElement
    }
}

fun<T, Base> operatorTable(element: Parser<T, Base>, body: OperatorTableContext<T, Base>.() -> Unit): Parser<T, Base> {
    val ctx = OperatorTableContext(element)
    ctx.body()
    return ctx.build()
}
