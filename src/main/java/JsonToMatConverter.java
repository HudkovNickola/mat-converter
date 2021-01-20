import org.json.JSONArray;
import org.json.JSONObject;
import us.hebi.matlab.mat.types.Array;
import us.hebi.matlab.mat.types.Cell;
import us.hebi.matlab.mat.types.Char;
import us.hebi.matlab.mat.types.FunctionHandle;
import us.hebi.matlab.mat.types.JavaObject;
import us.hebi.matlab.mat.types.MatFile;
import us.hebi.matlab.mat.types.Matrix;
import us.hebi.matlab.mat.types.ObjectStruct;
import us.hebi.matlab.mat.types.Struct;

import java.util.Arrays;
import java.util.Objects;

final class JsonToMatConverter {

    static String convert(Iterable<MatFile.Entry> entries) {
        final JSONArray objects = buildJson(entries);
        return objects.toString();

    }

    private static JSONArray buildJson(Iterable<MatFile.Entry> entries) {
        final JSONArray objects = new JSONArray();
        for (final MatFile.Entry entry : entries) {
            final JSONObject jsonObject = new JSONObject();
            final JSONObject rootObject = putRootObject(entry.getName(), jsonObject);
            buildJsonBody(entry.getValue(), null, rootObject, null);
            objects.put(jsonObject);
        }
        return objects;
    }

    private static void buildJsonBody(Array array, String field, JSONObject rootJsonObject, JSONObject prevJesonObject) {
        // Special case empty arrays
        if (buildEmptyObjects(array, field, prevJesonObject)) return;
        // Special case char strings
        if (buildCharObjects(array, field, prevJesonObject)) return;
        if (prevJesonObject != null) {
            prevJesonObject.put(field, rootJsonObject);
        }
        // Special case scalar matrices
        if (buildMatrixObject(array, field, prevJesonObject)) return;
        // Add sub-fields to Structs & Objects
        if (buildStuctObjects(array, rootJsonObject)) return;
        // Add content of function handles
        if (buildFunctionObjects(array, rootJsonObject)) return;
        // Add meta info for Opaque fields
        if (buildJavaObjects(array, field, rootJsonObject)) return;
        // Generic fallback
        if (prevJesonObject != null) {
            appendGenericString(array, field, prevJesonObject);
        }
    }

    private static boolean buildJavaObjects(Array array, String field, JSONObject rootJsonObject) {
        if (array instanceof JavaObject && Objects.nonNull(field)) {
            JavaObject javaObj = (JavaObject) array;
            final String javaObject = String.format("%s %s (Java)", getDimString(array), javaObj.getClassName());
            rootJsonObject.put(field, javaObject);
            return true;
        }
        return false;
    }

    private static boolean buildFunctionObjects(Array array, JSONObject rootJsonObject) {
        if (array instanceof FunctionHandle) {
            rootJsonObject.put("function_handle", getDimString(array));
            rootJsonObject.put("content", ((FunctionHandle) array).getContent());
            return true;
        }
        return false;
    }

    private static boolean buildStuctObjects(Array array, JSONObject rootJsonObject) {
        if (array instanceof Struct) {
            // Class w/ dimensions
            String className = "struct";
            if (array instanceof ObjectStruct)
                className = getFullClassName((ObjectStruct) array);
            final String type = String.format("%s %s", getDimString(array), className);
            rootJsonObject.put("type", type);
            // Find longest field
            Struct struct = (Struct) array;
            for (String fieldName : struct.getFieldNames()) {
                // List values only if the struct is scalar
                if (struct.getNumElements() == 1) {
                    JSONObject jsonObject = new JSONObject();
                    buildJsonBody(struct.get(fieldName), fieldName, jsonObject, rootJsonObject);
                }
            }
            return true;
        }
        return false;
    }

    private static boolean buildMatrixObject(Array array, String field, JSONObject prevJesonObject) {
        if (array instanceof Matrix) {
            final Matrix matrix = (Matrix) array;
            if (matrix.getNumElements() == 1 && Objects.nonNull(field) && Objects.nonNull(prevJesonObject)) {
                if (matrix.isLogical()) {
                    prevJesonObject.put(field, matrix.getBoolean(0));
                    return true;
                }
                prevJesonObject.put(field, matrix.getDouble(0));
                if (matrix.isComplex()) {
                    final String complex = String.format("+%sj", matrix.getImaginaryDouble(0));
                    prevJesonObject.put(field, complex);
                }
                return true;
            }
        }
        return false;
    }

    private static boolean buildCharObjects(Array array, String field, JSONObject prevJesonObject) {
        if (array instanceof Char && Objects.nonNull(field)) {
            Char c = (Char) array;
            if (c.getNumRows() == 1 && Objects.nonNull(prevJesonObject)) {
                prevJesonObject.put(field, c.getString());
                return true;
            }
        }
        return false;
    }

    private static boolean buildEmptyObjects(Array array, String field, JSONObject prevJesonObject) {
        if (array.getNumElements() == 0 && Objects.nonNull(field) && Objects.nonNull(prevJesonObject)) {
            if (array instanceof Cell) {
                prevJesonObject.put(field, new JSONObject());
                return true;
            } else if (array instanceof Matrix) {
                prevJesonObject.put(field, new JSONArray());
                return true;
            } else if (array instanceof Char) {
                prevJesonObject.put(field, "");
                return true;
            }
        }
        return false;
    }

    private static void appendGenericString(Array array, String filedName, JSONObject jsonObject) {
        jsonObject.put(filedName, String.format("%s %s", getDimString(array), array.getType()));
    }

    private static String getDimString(Array array) {
        return Arrays.toString(array.getDimensions())
                .replaceAll(", ", "x")
                .replace("[", "")
                .replace("]", " ");
    }

    private static JSONObject putRootObject(final String name, final JSONObject jsonObject) {
        final String result = name.isEmpty() ? "\"\"" : name;
        final JSONObject rootJsonObject = new JSONObject();
        jsonObject.put(result, rootJsonObject);
        return rootJsonObject;
    }

    private static String getFullClassName(ObjectStruct object) {
        if (!object.getPackageName().isEmpty())
            return object.getPackageName() + "." + object.getClassName();
        return object.getClassName();
    }
}
