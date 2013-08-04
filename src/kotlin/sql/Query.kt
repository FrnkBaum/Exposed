package kotlin.sql

import java.sql.Connection
import java.util.HashSet
import java.util.ArrayList
import java.util.HashMap
import java.sql.ResultSet
import kotlin.properties.Delegates
import java.util.NoSuchElementException

public class ResultRow(val rs: ResultSet, fields: List<Field<*>>) {
    val data = HashMap<Field<*>, Any?>();
    {
        fields.forEachWithIndex { (i, f) -> data[f] = rs.getObject(i + 1) }
    }

    fun <T> get(c: Field<T>) : T {
        val d:Any? = when {
            data.containsKey(c) -> data[c]
            else -> throw RuntimeException("${c.toSQL()} is not in record set")
        }

        return d as T
    }
}

open class Query(val session: Session, val set: FieldSet, val where: Op?): Iterable<ResultRow> {
    var selectedColumns = HashSet<Column<*>>();
    val groupedByColumns = ArrayList<Column<*>>();

    private val statement: String by Delegates.lazy {
        val sql = StringBuilder("SELECT ")

        with(sql) {
            append((set.fields map {it.toSQL()}).makeString(", ", "", ""))
            append(" FROM ")
            append(set.source.describe(session))

            if (where != null) {
                append(" WHERE ")
                append(where.toSQL())
            }

            if (groupedByColumns.size > 0) {
                append(" GROUP BY ")
                append((groupedByColumns map {session.fullIdentity(it)}).makeString(", ", "", ""))
            }
        }

        println("SQL: " + sql.toString())
        sql.toString()
    }

    fun groupBy(vararg columns: Column<*>): Query {
        for (column in columns) {
            groupedByColumns.add(column)
        }
        return this
    }

    private inner class ResultIterator(val rs: ResultSet): Iterator<ResultRow> {
        private var hasNext: Boolean? = null

        public override fun next(): ResultRow {
            if (hasNext == null) hasNext()
            if (hasNext == false) throw NoSuchElementException()
            hasNext = null
            return ResultRow(rs, set.fields)
        }

        public override fun hasNext(): Boolean {
            if (hasNext == null) hasNext = rs.next()
            return hasNext!!
        }
    }

    public override fun iterator(): Iterator<ResultRow> {
        val rs = session.connection.createStatement()?.executeQuery(statement)!!
        return ResultIterator(rs)
    }
}