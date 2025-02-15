/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.planner.functions;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.config.ExecutionConfigOptions;
import org.apache.flink.table.functions.BuiltInFunctionDefinitions;
import org.apache.flink.table.functions.ScalarFunction;
import org.apache.flink.types.Row;

import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.apache.flink.table.api.DataTypes.BIGINT;
import static org.apache.flink.table.api.DataTypes.BINARY;
import static org.apache.flink.table.api.DataTypes.BOOLEAN;
import static org.apache.flink.table.api.DataTypes.BYTES;
import static org.apache.flink.table.api.DataTypes.FIELD;
import static org.apache.flink.table.api.DataTypes.INT;
import static org.apache.flink.table.api.DataTypes.ROW;
import static org.apache.flink.table.api.DataTypes.STRING;
import static org.apache.flink.table.api.DataTypes.TIME;
import static org.apache.flink.table.api.DataTypes.TINYINT;
import static org.apache.flink.table.api.DataTypes.VARBINARY;
import static org.apache.flink.table.api.Expressions.$;
import static org.apache.flink.table.api.Expressions.call;
import static org.apache.flink.table.api.Expressions.row;

/** Tests for {@link BuiltInFunctionDefinitions#CAST} regarding {@link DataTypes#ROW}. */
public class CastFunctionMiscITCase extends BuiltInFunctionTestBase {

    @Override
    protected Configuration configuration() {
        return super.configuration()
                .set(
                        ExecutionConfigOptions.TABLE_EXEC_LEGACY_CAST_BEHAVIOUR,
                        ExecutionConfigOptions.LegacyCastBehaviour.DISABLED);
    }

    @Parameterized.Parameters(name = "{index}: {0}")
    public static List<TestSpec> testData() {
        return Arrays.asList(
                TestSpec.forFunction(
                                BuiltInFunctionDefinitions.CAST,
                                "implicit with different field names")
                        .onFieldsWithData(Row.of(12, "Hello"))
                        .andDataTypes(DataTypes.of("ROW<otherNameInt INT, otherNameString STRING>"))
                        .withFunction(RowToFirstField.class)
                        .testResult(
                                call("RowToFirstField", $("f0")), "RowToFirstField(f0)", 12, INT()),
                TestSpec.forFunction(BuiltInFunctionDefinitions.CAST, "implicit with type widening")
                        .onFieldsWithData(Row.of((byte) 12, "Hello"))
                        .andDataTypes(DataTypes.of("ROW<i TINYINT, s STRING>"))
                        .withFunction(RowToFirstField.class)
                        .testResult(
                                call("RowToFirstField", $("f0")), "RowToFirstField(f0)", 12, INT()),
                TestSpec.forFunction(
                                BuiltInFunctionDefinitions.CAST,
                                "implicit with nested type widening")
                        .onFieldsWithData(Row.of(Row.of(12, 42), "Hello"))
                        .andDataTypes(DataTypes.of("ROW<r ROW<i1 INT, i2 INT>, s STRING>"))
                        .withFunction(NestedRowToFirstField.class)
                        .testResult(
                                call("NestedRowToFirstField", $("f0")),
                                "NestedRowToFirstField(f0)",
                                Row.of(12, 42.0),
                                DataTypes.of("ROW<i INT, d DOUBLE>")),
                TestSpec.forFunction(
                                BuiltInFunctionDefinitions.CAST,
                                "explicit with nested rows and implicit nullability change")
                        .onFieldsWithData(Row.of(Row.of(12, 42, null), "Hello"))
                        .andDataTypes(DataTypes.of("ROW<r ROW<i1 INT, i2 INT, i3 INT>, s STRING>"))
                        .testResult(
                                $("f0").cast(
                                                ROW(
                                                        FIELD(
                                                                "r",
                                                                ROW(
                                                                        FIELD("s", STRING()),
                                                                        FIELD("b", BOOLEAN()),
                                                                        FIELD("i", INT()))),
                                                        FIELD("s", STRING()))),
                                "CAST(f0 AS ROW<r ROW<s STRING NOT NULL, b BOOLEAN, i INT>, s STRING>)",
                                Row.of(Row.of("12", true, null), "Hello"),
                                // the inner NOT NULL is ignored in SQL because the outer ROW is
                                // nullable and the cast does not allow setting the outer
                                // nullability but derives it from the source operand
                                DataTypes.of("ROW<r ROW<s STRING, b BOOLEAN, i INT>, s STRING>")),
                TestSpec.forFunction(
                                BuiltInFunctionDefinitions.CAST,
                                "explicit with nested rows and explicit nullability change")
                        .onFieldsWithData(Row.of(Row.of(12, 42, null), "Hello"))
                        .andDataTypes(DataTypes.of("ROW<r ROW<i1 INT, i2 INT, i3 INT>, s STRING>"))
                        .testTableApiResult(
                                $("f0").cast(
                                                ROW(
                                                        FIELD(
                                                                "r",
                                                                ROW(
                                                                        FIELD(
                                                                                "s",
                                                                                STRING().notNull()),
                                                                        FIELD("b", BOOLEAN()),
                                                                        FIELD("i", INT()))),
                                                        FIELD("s", STRING()))),
                                Row.of(Row.of("12", true, null), "Hello"),
                                DataTypes.of(
                                        "ROW<r ROW<s STRING NOT NULL, b BOOLEAN, i INT>, s STRING>")),
                TestSpec.forFunction(
                                BuiltInFunctionDefinitions.CAST,
                                "implicit between structured type and row")
                        .onFieldsWithData(12, "Ingo")
                        .withFunction(StructuredTypeConstructor.class)
                        .withFunction(RowToFirstField.class)
                        .testResult(
                                call(
                                        "RowToFirstField",
                                        call("StructuredTypeConstructor", row($("f0"), $("f1")))),
                                "RowToFirstField(StructuredTypeConstructor((f0, f1)))",
                                12,
                                INT()),
                TestSpec.forFunction(
                                BuiltInFunctionDefinitions.CAST,
                                "explicit between structured type and row")
                        .onFieldsWithData(12, "Ingo")
                        .withFunction(StructuredTypeConstructor.class)
                        .testTableApiResult(
                                call("StructuredTypeConstructor", row($("f0"), $("f1")))
                                        .cast(ROW(BIGINT(), STRING())),
                                Row.of(12L, "Ingo"),
                                ROW(BIGINT(), STRING())),
                TestSpec.forFunction(
                                BuiltInFunctionDefinitions.CAST,
                                "cast from RAW(Integer) to BINARY(3)")
                        .onFieldsWithData(123456)
                        .andDataTypes(INT())
                        .withFunction(IntegerToRaw.class)
                        .testTableApiResult(
                                call("IntegerToRaw", $("f0")).cast(BINARY(3)),
                                new byte[] {0, 1, -30},
                                BINARY(3)),
                TestSpec.forFunction(
                                BuiltInFunctionDefinitions.CAST, "cast from RAW(Integer) to BYTES")
                        .onFieldsWithData(123456)
                        .andDataTypes(INT())
                        .withFunction(IntegerToRaw.class)
                        .testTableApiResult(
                                call("IntegerToRaw", $("f0")).cast(BYTES()),
                                new byte[] {0, 1, -30, 64},
                                BYTES()),
                TestSpec.forFunction(
                                BuiltInFunctionDefinitions.CAST,
                                "cast from RAW(Integer) to BINARY(6)")
                        .onFieldsWithData(123456)
                        .andDataTypes(INT())
                        .withFunction(IntegerToRaw.class)
                        .testTableApiResult(
                                call("IntegerToRaw", $("f0")).cast(BINARY(6)),
                                new byte[] {0, 1, -30, 64, 0, 0},
                                BINARY(6)),
                TestSpec.forFunction(
                                BuiltInFunctionDefinitions.CAST,
                                "cast from RAW(UserPojo) to VARBINARY")
                        .onFieldsWithData(123456, "Flink")
                        .andDataTypes(INT(), STRING())
                        .withFunction(StructuredTypeConstructor.class)
                        .withFunction(PojoToRaw.class)
                        .testTableApiResult(
                                call(
                                                "PojoToRaw",
                                                call(
                                                        "StructuredTypeConstructor",
                                                        row($("f0"), $("f1"))))
                                        .cast(VARBINARY(50)),
                                new byte[] {0, 1, -30, 64, 0, 70, 0, 108, 0, 105, 0, 110, 0, 107},
                                VARBINARY(50)),
                TestSpec.forFunction(
                                BuiltInFunctionDefinitions.CAST, "test the x'....' binary syntax")
                        .onFieldsWithData("foo")
                        .testSqlResult(
                                "CAST(CAST(x'68656C6C6F20636F6465' AS BINARY(10)) AS VARCHAR)",
                                "68656c6c6f20636f6465",
                                STRING().notNull()),
                TestSpec.forFunction(
                                BuiltInFunctionDefinitions.CAST, "test the x'....' binary syntax")
                        .onFieldsWithData("foo")
                        .testSqlResult(
                                "CAST(CAST(x'68656C6C6F2063617374' AS BINARY(10)) AS VARCHAR)",
                                "68656c6c6f2063617374",
                                STRING().notNull()),
                TestSpec.forFunction(
                                BuiltInFunctionDefinitions.TRY_CAST, "try cast from STRING to TIME")
                        .onFieldsWithData("Flink", "12:34:56")
                        .andDataTypes(STRING(), STRING())
                        .testResult(
                                $("f0").tryCast(TIME()),
                                "TRY_CAST(f0 AS TIME)",
                                null,
                                TIME().nullable())
                        .testResult(
                                $("f1").tryCast(TIME()),
                                "TRY_CAST(f1 AS TIME)",
                                LocalTime.of(12, 34, 56, 0),
                                TIME().nullable()),
                TestSpec.forFunction(
                                BuiltInFunctionDefinitions.TRY_CAST,
                                "try cast from TIME NOT NULL to STRING NOT NULL")
                        .onFieldsWithData(LocalTime.parse("12:34:56"))
                        .andDataTypes(TIME().notNull())
                        .testResult(
                                $("f0").tryCast(STRING()),
                                "TRY_CAST(f0 AS STRING)",
                                "12:34:56",
                                STRING().nullable()),
                TestSpec.forFunction(
                                BuiltInFunctionDefinitions.TRY_CAST,
                                "try cast from ROW<INT, STRING> to ROW<TINYINT, TIME>")
                        .onFieldsWithData(Row.of(1, "abc"), Row.of(1, "12:34:56"))
                        .andDataTypes(ROW(INT(), STRING()), ROW(INT(), STRING()))
                        .testResult(
                                $("f0").tryCast(ROW(TINYINT(), TIME())),
                                "TRY_CAST(f0 AS ROW(f0 TINYINT, f1 TIME))",
                                null,
                                ROW(TINYINT(), TIME()).nullable())
                        .testResult(
                                $("f1").tryCast(ROW(TINYINT(), TIME())),
                                "TRY_CAST(f1 AS ROW(f0 TINYINT, f1 TIME))",
                                Row.of((byte) 1, LocalTime.of(12, 34, 56, 0)),
                                ROW(TINYINT(), TIME()).nullable()));
    }

    // --------------------------------------------------------------------------------------------

    /** Function that return the first field of the input row. */
    public static class RowToFirstField extends ScalarFunction {
        public Integer eval(@DataTypeHint("ROW<i INT, s STRING>") Row row) {
            assert row.getField(0) instanceof Integer;
            assert row.getField(1) instanceof String;
            return (Integer) row.getField(0);
        }
    }

    /** Function that return the first field of the nested input row. */
    public static class NestedRowToFirstField extends ScalarFunction {
        public @DataTypeHint("ROW<i INT, d DOUBLE>") Row eval(
                @DataTypeHint("ROW<r ROW<i INT, d DOUBLE>, s STRING>") Row row) {
            assert row.getField(0) instanceof Row;
            assert row.getField(1) instanceof String;
            return (Row) row.getField(0);
        }
    }

    /** Function that takes and forwards a structured type. */
    public static class StructuredTypeConstructor extends ScalarFunction {
        public UserPojo eval(UserPojo pojo) {
            return pojo;
        }
    }

    /** Testing POJO. */
    public static class UserPojo {
        public final Integer i;

        public final String s;

        public UserPojo(Integer i, String s) {
            this.i = i;
            this.s = s;
        }
    }

    /** Test Raw with built-in Java class. */
    public static class IntegerToRaw extends ScalarFunction {

        public @DataTypeHint(value = "RAW", bridgedTo = byte[].class) byte[] eval(Integer i) {
            ByteBuffer b = ByteBuffer.allocate(4);
            b.putInt(i);
            return b.array();
        }
    }

    /** Test Raw with custom class. */
    public static class PojoToRaw extends ScalarFunction {

        public @DataTypeHint(value = "RAW", bridgedTo = byte[].class) byte[] eval(UserPojo up) {
            ByteBuffer b = ByteBuffer.allocate((up.s.length() * 2) + 4);
            b.putInt(up.i);
            for (char c : up.s.toCharArray()) {
                b.putChar(c);
            }
            return b.array();
        }
    }
}
