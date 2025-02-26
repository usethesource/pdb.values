/*******************************************************************************
 * Copyright (c) CWI 2008
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Jurgen Vinju (jurgenv@cwi.nl) - initial API and implementation

 *******************************************************************************/

package io.usethesource.vallang.io;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.checkerframework.checker.nullness.qual.NonNull;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.usethesource.vallang.IListWriter;
import io.usethesource.vallang.IMapWriter;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.IWriter;
import io.usethesource.vallang.exceptions.FactParseError;
import io.usethesource.vallang.exceptions.FactTypeUseException;
import io.usethesource.vallang.exceptions.OverloadingNotSupportedException;
import io.usethesource.vallang.exceptions.UnexpectedTypeException;
import io.usethesource.vallang.type.ExternalType;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

/**
 * This class implements the standard readable syntax for {@link IValue}'s.
 * Note that the parser also validates the input according to a given {@link Type}.
 *
 * Note however that overloaded constructors for abstract data-types are <b>not</b> supported.
 *
 * See also {@link StandardTextWriter}
 */
public class StandardTextReader extends AbstractTextReader {

    public IValue read(IValueFactory factory, TypeStore store, Type type, Reader stream) throws FactTypeUseException, IOException {
        return new TextReader(factory, store, stream).read(type);
    }

    /**
     * private class handling the temporary parser state for a single stream.
     */
    private static class TextReader {
        private static final char START_OF_LOC = '|';
        private static final char START_OF_STRING = '\"';
        private static final char END_OF_STRING = '\"';
        private static final char START_OF_MAP = '(';
        private static final char START_OF_ARGUMENTS = '(';
        private static final char END_OF_ARGUMENTS = ')';
        private static final char START_OF_TUPLE = '<';
        private static final char START_OF_SET = '{';
        private static final char START_OF_LIST = '[';
        private static final char END_OF_TUPLE = '>';
        private static final char COMMA_SEPARATOR = ',';
        private static final char END_OF_MAP = ')';
        private static final char DOUBLE_DOT = '.';
        private static final char RATIONAL_SEP = 'r';
        private static final char END_OF_SET = '}';
        private static final char END_OF_LIST = ']';
        private static final char END_OF_LOCATION = '|';
        private static final char START_OF_DATETIME = '$';
        private static final char END_OF_DATETIME = '$';

        private static final char NEGATIVE_SIGN = '-';
        private static final TypeFactory types = TypeFactory.getInstance();

        private final TypeStore store;
        private final NoWhiteSpaceReader stream;
        private final IValueFactory factory;

        private int current;
        private Cache<String, ISourceLocation> sourceLocationCache;

        public TextReader(IValueFactory factory, TypeStore store, Reader stream) {
            this.store = store;
            this.stream = new NoWhiteSpaceReader(stream);
            this.factory = factory;
            this.sourceLocationCache = Caffeine.newBuilder().maximumSize(1000).build();
        }

        public IValue read(Type expected) throws IOException {
            current = stream.read();
            IValue result = readValue(expected);

            if (current != -1 || this.stream.read() != -1) {
                throw unexpectedException();
            }

            return result;
        }

        private IValue readValue(Type expected) throws IOException {
            IValue result = null;

            if (Character.isDigit(current) || current == DOUBLE_DOT || current == NEGATIVE_SIGN) {
                result = readNumber(expected);
            }
            else if ((Character.isJavaIdentifierStart(current) && '$' != current)
                    || current == '\\') {
                boolean escaped = current == '\\';
                String id = readIdentifier();

                if (!escaped && id.equals("true") && !expected.isAbstractData()) {
                    return factory.bool(true);
                }
                else if (!escaped && id.equals("false") && !expected.isAbstractData()) {
                    return factory.bool(false);
                }
                else if (current == '=') {
                    return factory.string(id);
                }
                else if (current == START_OF_ARGUMENTS) {
                    result = readConstructor(id, expected);
                }
                else {
                    throw new FactParseError("expected = or (", stream.offset);
                }
            }
            else {
                switch (current) {
                    case START_OF_STRING:
                        result = readString(expected);
                        break;
                    case START_OF_LIST:
                        result = readList(expected);
                        break;
                    case START_OF_SET:
                        result = readSet(expected);
                        break;
                    case START_OF_TUPLE:
                        result = readTuple(expected);
                        break;
                    case START_OF_MAP:
                        result = readMap(expected);
                        break;
                    case START_OF_LOC:
                        result = readLocation(expected);
                        break;
                    case START_OF_DATETIME:
                        result = readDateTime(expected);
                        break;
                    default:
                        throw unexpectedException();
                }
            }

            assert result != null : "@AssumeAssertion(nullness)";

            if (!result.getType().isSubtypeOf(expected)) {
                throw new UnexpectedTypeException(expected, result.getType());
            }

            if (current == '[') {
                if (result.getType().isSubtypeOf(types.nodeType())) {
                    result = readAnnos(expected, (INode) result);
                }
                else {
                    throw unexpectedException((int) ']');
                }
            }

            return result;
        }


        private IValue readLocation(Type expected) throws IOException {
            try {

                String url = parseURL();
                ISourceLocation loc = getCachedLocation(url);

                if (current == START_OF_ARGUMENTS) {
                    ArrayList<IValue> args = new ArrayList<>(4);
                    readFixed(types.valueType(), ')', args, Collections.emptyMap());

                    if (args.size() >= 2) {
                        if (!args.get(0).getType().isSubtypeOf(types.integerType())) {
                            throw new UnexpectedTypeException(types.integerType(), args.get(0).getType());
                        }
                        if (!args.get(1).getType().isSubtypeOf(types.integerType())) {
                            throw new UnexpectedTypeException(types.integerType(), args.get(1).getType());
                        }

                        Type posType = types.tupleType(types.integerType(), types.integerType());

                        if (args.size() == 4) {
                            if (!args.get(2).getType().isSubtypeOf(posType)) {
                                throw new UnexpectedTypeException(posType, args.get(2).getType());
                            }
                            if (!args.get(3).getType().isSubtypeOf(posType)) {
                                throw new UnexpectedTypeException(posType, args.get(3).getType());
                            }
                        }

                        int offset = Integer.parseInt(args.get(0).toString());
                        int length = Integer.parseInt(args.get(1).toString());

                        if (args.size() == 4) {
                            int beginLine = Integer.parseInt(((ITuple) args.get(2)).get(0).toString());
                            int beginColumn = Integer.parseInt(((ITuple) args.get(2)).get(1).toString());
                            int endLine = Integer.parseInt(((ITuple) args.get(3)).get(0).toString());
                            int endColumn = Integer.parseInt(((ITuple) args.get(3)).get(1).toString());

                            return factory.sourceLocation(loc, offset, length, beginLine, endLine, beginColumn, endColumn);
                        }

                        if (args.size() != 2) {
                            throw new FactParseError("source locations should have either 2 or 4 arguments", offset);
                        }

                        return factory.sourceLocation(loc, offset, length);
                    }
                }

                return loc;

            } catch (RuntimeException e) {
                if (e.getCause() instanceof URISyntaxException) {
                    URISyntaxException actual = (URISyntaxException) e.getCause();
                    throw new FactParseError(actual.getMessage(), stream.offset, actual);
                }
                throw e;
            }
        }

        private ISourceLocation getCachedLocation(String url) {
            assert sourceLocationCache != null : "@AssumeAssertion(nullness)";

            ISourceLocation loc = sourceLocationCache.get(url, u -> {
                try {
                    return factory.sourceLocation(new URI(u));
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            });

            assert loc != null : "@AssumeAssertion(nullness)";
            return loc;
        }

        private String parseURL() throws IOException {
            current = stream.read();
            StringBuilder result = new StringBuilder();

            while (current != END_OF_LOCATION) {
                result.append((char) current);
                current = stream.read();
                if (current == -1) {
                    throw unexpectedException();
                }
            }

            current = stream.read();

            return result.toString();
        }

        private static final TypeFactory TF = TypeFactory.getInstance();
        private static final Type generalMapType = TF.mapType(TF.valueType(), TF.valueType());

        private IValue readMap(Type expected) throws IOException {
            Type keyType = expected.isSubtypeOf(generalMapType) ? expected.getKeyType() : types.valueType();
            Type valueType = expected.isSubtypeOf(generalMapType) ? expected.getValueType() : types.valueType();
            IMapWriter w = factory.mapWriter();

            checkAndRead(START_OF_MAP);

            while (current != END_OF_MAP) {
                IValue key = readValue(keyType);
                checkAndRead(':');
                IValue value = readValue(valueType);
                w.put(key, value);

                if (current != COMMA_SEPARATOR || current == END_OF_MAP) {
                    break; // no more elements, so expecting a ')'
                }
                else {
                    checkAndRead(COMMA_SEPARATOR);
                }
            }

            checkAndRead(END_OF_MAP);

            return w.done();
        }

        private IValue readTuple(Type expected) throws IOException {
            ArrayList<IValue> arr = new ArrayList<>();
            readFixed(expected, END_OF_TUPLE, arr, Collections.emptyMap());
            IValue[] result = arr.toArray(new IValue[0]);
            return factory.tuple(result);
        }

        private static final Type genericSetType = TF.setType(TF.valueType());

        private IValue readSet(Type expected) throws FactTypeUseException, IOException {
            Type elemType = expected.isSubtypeOf(genericSetType) ? expected.getElementType() : types.valueType();
            return readContainer(elemType, factory.setWriter(), END_OF_SET);
        }

        private static final Type genericListType = TF.listType(TF.valueType());

        private IValue readList(Type expected) throws FactTypeUseException, IOException {
            Type elemType = expected.isSubtypeOf(genericListType) ? expected.getElementType() : types.valueType();
            return readList(elemType, factory.listWriter(), END_OF_LIST);
        }

        private void checkMoreThanOnce(String input, char needle) {
            boolean first = true;
            for (int i=0; i < input.length(); i++) {
                if (input.charAt(i) == needle) {
                    if (first) {
                        first = false;
                    }
                    else {
                        throw new FactParseError(needle +" occured for the second time", (this.stream.offset - input.length()) + i);
                    }
                }
            }
        }


        private IValue readNumber(Type expected) throws IOException {
            StringBuilder builder = new StringBuilder();

            do{
                builder.append((char) current);
                current = stream.read();
            } while(Character.isDigit(current) || current == RATIONAL_SEP || current == DOUBLE_DOT || current == 'E' || current == 'e' || current == '+' || current == '-');

            String val = builder.toString();
            checkMoreThanOnce(val, RATIONAL_SEP);
            checkMoreThanOnce(val, DOUBLE_DOT);
            checkMoreThanOnce(val, 'E');
            checkMoreThanOnce(val, 'e');

            try {
                if (val.contains(".") || val.contains("E") || val.contains("e")) {
                    return factory.real(val);
                }

                return factory.integer(val);
            }
            catch (NumberFormatException e) {
                // could happen
            }

            try {
                return factory.real(val);
            }
            catch (NumberFormatException e) {
                // could happen
            }

            try {
                return factory.rational(val);
            }
            catch (NumberFormatException e) {
                // could happen
            }

            throw unexpectedException(current);
        }

        private IValue readConstructor(String id, Type expected) throws IOException {
            ArrayList<IValue> arr = new ArrayList<IValue>();
            Type args = expected;
            Type constr = null;
            if (expected.isExternalType()) {
                expected = ((ExternalType) expected).asAbstractDataType();
            }
            if (expected.isAbstractData() ) {
                Set<Type> alternatives = store.lookupConstructor(expected, id);
                if (alternatives.size() > 1) {
                    throw new OverloadingNotSupportedException(expected, id);
                }
                else if (alternatives.size() == 0) {
                    args = types.valueType();
                    // TODO: Should not it be an undeclared abstract data/constructor exception?!
                }
                else {
                    constr = alternatives.iterator().next();
                    args = constr.getFieldTypes();
                }
            }

            Map<String, IValue> kwParams = new HashMap<>();
            readFixed(args, END_OF_ARGUMENTS, arr, kwParams);

            @NonNull IValue[] result = arr.toArray(new IValue[0]);

            if (expected.isTop()) {
                constr = store.lookupFirstConstructor(id, TF.tupleType(result));
            }

            if (constr != null) {
                return factory.constructor(constr, result, kwParams);
            } else {
                return factory.node(id, result, kwParams);
            }
        }

        /**
         * Read in a single character from the input stream and append it to the
         * given buffer only if it is numeric.
         *
         * @param buf   The buffer to which the read character should be appended
         *
         * @return      True if the input character was numeric [0-9] and was appended,
         *              false otherwise.
         *
         * @throws IOException  when an error is encountered reading the input stream
         */
        private boolean readAndAppendIfNumeric(StringBuilder buf) throws IOException {
            current = stream.read();
            if (Character.isDigit(current)) {
                buf.append((char)current);
                return true;
            } else {
                return false;
            }
        }

        /**
         * Read in a date value, given as a string of the form NNNN-NN-NN, where each
         * N is a digit [0-9]. The groups are the year, month, and day of month.
         *
         * @return A DateParts object with year, month, and day filled in.
         *
         * @throws IOException  when an error is encountered reading the input stream
         * @throws FactParseError   when the correct characters are not found while lexing
         *                          the date
         */
        private DateParts readDate(char firstChar) throws IOException, FactParseError {
            StringBuilder buf = new StringBuilder();
            buf.append(firstChar);

            // The first four characters should be the year
            for (int i = 0; i < 3; ++i) {
                boolean res = readAndAppendIfNumeric(buf);
                if (!res) {
                    throw new FactParseError("Error reading date, expected digit, found: " + current, stream.offset);
                }
            }

            // The next character should be a '-'
            current = stream.read();
            if ('-' != current) {
                throw new FactParseError("Error reading date, expected '-', found: " + current, stream.offset);
            }

            // The next two characters should be the month
            for (int i = 0; i < 2; ++i) {
                boolean res = readAndAppendIfNumeric(buf);
                if (!res) {
                    throw new FactParseError("Error reading date, expected digit, found: " + current, stream.offset);
                }
            }

            // The next character should be a '-'
            current = stream.read();
            if ('-' != current) {
                throw new FactParseError("Error reading date, expected '-', found: " + current, stream.offset);
            }

            // The next two characters should be the day
            for (int i = 0; i < 2; ++i) {
                boolean res = readAndAppendIfNumeric(buf);
                if (!res) {
                    throw new FactParseError("Error reading date, expected digit, found: " + current, stream.offset);
                }
            }

            String dateString = buf.toString();
            return new DateParts(
                    Integer.parseInt(dateString.substring(0, 4)), // year
                    Integer.parseInt(dateString.substring(4,6)), // month
                    Integer.parseInt(dateString.substring(6))); // day
        }

        /**
         * Read in a time value, given as a string of the form NN:NN:NN.NNN[+-]NN:NN,
         * where each N is a digit [0-9]. The groups are the hour, minute, second,
         * millisecond, timezone hour offset, and timezone minute offset.
         *
         * @return A TimeParts objects with hours, minutes, seconds, milliseconds,
         *         and timezone information filled in.
         *
         * @throws IOException  when an error is encountered reading the input stream
         * @throws FactParseError   when the correct characters are not found while lexing
         *                          the time
         */
        private TimeParts readTime() throws IOException, FactParseError {
            StringBuilder buf = new StringBuilder();

            // The first two characters should be the hour
            for (int i = 0; i < 2; ++i) {
                boolean res = readAndAppendIfNumeric(buf);
                if (!res) {
                    throw new FactParseError("Error reading time, expected digit, found: " + current, stream.offset);
                }
            }

            // The next character should be a ':'
            current = stream.read();
            if (':' != current) {
                throw new FactParseError("Error reading time, expected ':', found: " + current, stream.offset);
            }

            // The next two characters should be the minute
            for (int i = 0; i < 2; ++i) {
                boolean res = readAndAppendIfNumeric(buf);
                if (!res) {
                    throw new FactParseError("Error reading time, expected digit, found: " + current, stream.offset);
                }
            }

            // The next character should be a ':'
            current = stream.read();
            if (':' != current) {
                throw new FactParseError("Error reading time, expected ':', found: " + current, stream.offset);
            }

            // The next two characters should be the second
            for (int i = 0; i < 2; ++i) {
                boolean res = readAndAppendIfNumeric(buf);
                if (!res) {
                    throw new FactParseError("Error reading time, expected digit, found: " + current, stream.offset);
                }
            }

            // The next character should be a '.'
            current = stream.read();
            if ('.' != current) {
                throw new FactParseError("Error reading time, expected '.', found: " + current, stream.offset);
            }

            // The next three characters should be the millisecond
            for (int i = 0; i < 3; ++i) {
                boolean res = readAndAppendIfNumeric(buf);
                if (!res) {
                    throw new FactParseError("Error reading time, expected digit, found: " + current, stream.offset);
                }
            }

            // The next character should be '+' or '-'
            current = stream.read();
            if (! ('+' == current || '-' == current)) {
                throw new FactParseError("Error reading time, expected '+' or '-', found: " + current, stream.offset);
            }
            buf.append((char)current);

            // The next two characters should be the hour offset
            for (int i = 0; i < 2; ++i) {
                boolean res = readAndAppendIfNumeric(buf);
                if (!res) {
                    throw new FactParseError("Error reading time, expected digit, found: " + current, stream.offset);
                }
            }

            // The next two characters should be the minute offset
            for (int i = 0; i < 2; ++i) {
                boolean res = readAndAppendIfNumeric(buf);
                if (current == ':' && i == 0) {
                    // skip optional : separator between hour and minute offset
                    i = -1;
                    res = true;
                }
                if (!res) {
                    throw new FactParseError("Error reading time, expected digit, found: " + current, stream.offset);
                }
            }

            String timeString = buf.toString();
            boolean negativeTZHours = timeString.substring(9,10).equals("-");
            return new TimeParts(
                    Integer.parseInt(timeString.substring(0,2)), // hour
                    Integer.parseInt(timeString.substring(2,4)), // minutes
                    Integer.parseInt(timeString.substring(4,6)), // seconds
                    Integer.parseInt(timeString.substring(6,9)), // milliseconds
                    Integer.parseInt(timeString.substring(10,12)) * (negativeTZHours ? -1 : 1), // timezone hours
                    Integer.parseInt(timeString.substring(12)) * (negativeTZHours ? -1 : 1)); // timezone minutes
        }

        private IValue readDateTime(Type expected) throws IOException, FactParseError {
            DateParts dateParts = null;
            TimeParts timeParts = null;
            boolean isDate = false;
            boolean isTime = false;

            // Retrieve the string to parse and pick the correct format string,
            // based on whether we are reading a time, a date, or a datetime.
            current = stream.read();

            if ('T' == current || 't' == current) {
                timeParts = readTime();
                isTime = true;
                current = stream.read(); // advance to next character for parsing
            } else {
                dateParts = readDate((char)current);
                current = stream.read();
                if ('T' == current || 't' == current) {
                    timeParts = readTime();
                    current = stream.read(); // advance to next character for parsing
                } else {
                    isDate = true;
                    // no need to advance here, already did when checked for T
                }
            }

            if (END_OF_DATETIME == current) {
                // new-style datatime literal, consume the end character and advance
                current = stream.read();
            }

            if (isDate) {
                assert dateParts != null : "@AssumeAssertion(nullness)";

                return factory.date(dateParts.getYear(), dateParts.getMonth(), dateParts.getDay());
            } else {
                if (isTime) {
                    assert timeParts != null : "@AssumeAssertion(nullness)";

                    return factory.time(timeParts.getHour(), timeParts.getMinute(), timeParts.getSecond(), timeParts.getMillisecond(), timeParts.getTimezoneHours(), timeParts.getTimezoneMinutes());
                } else {
                    assert dateParts != null : "@AssumeAssertion(nullness)";
                    assert timeParts != null : "@AssumeAssertion(nullness)";

                    return factory.datetime(dateParts.getYear(), dateParts.getMonth(), dateParts.getDay(), timeParts.getHour(), timeParts.getMinute(), timeParts.getSecond(), timeParts.getMillisecond(), timeParts.getTimezoneHours(), timeParts.getTimezoneMinutes());
                }
            }
        }

        private String readIdentifier() throws IOException {
            StringBuilder builder = new StringBuilder();
            boolean escaped = (current == '\\');

            if (escaped) {
                current = stream.read();
            }

            while (Character.isJavaIdentifierStart(current)
                    || Character.isJavaIdentifierPart(current)
                    || (escaped && current == '-')) {
                builder.append((char) current);
                current = stream.read();
            }

            return builder.toString();
        }

        private IValue readString(Type expected) throws IOException {
            StringBuilder builder = new StringBuilder();
            current = stream.read();

            while (current != END_OF_STRING) {
                if (current == '\\') {
                    current = stream.read();
                    switch (current) {
                        case 'n':
                            builder.append('\n');
                            break;
                        case 't':
                            builder.append('\t');
                            break;
                        case 'r':
                            builder.append('\r');
                            break;
                        case 'f':
                            builder.append('\f');
                            break;
                        case 'b':
                            builder.append('\b');
                            break;
                        case '\"':
                            builder.append('\"');
                            break;
                        case '>':
                            builder.append('>');
                            break;
                        case '<':
                            builder.append('<');
                            break;
                        case '\'':
                            builder.append('\'');
                            break;
                        case '\\':
                            builder.append('\\');
                            break;
                        case 'a':
                            StringBuilder a = new StringBuilder();
                            a.append((char)stream.read());
                            a.append((char)stream.read());
                            builder.append((char) Integer.parseInt(a.toString(), 16));
                            break;
                        case 'u':
                            StringBuilder u = new StringBuilder();
                            u.append((char) stream.read());
                            u.append((char)stream.read());
                            u.append((char)stream.read());
                            u.append((char)stream.read());
                            builder.append((char) Integer.parseInt(u.toString(), 16));
                            break;
                        case 'U':
                            StringBuilder U = new StringBuilder();
                            U.append((char)stream.read());
                            U.append((char)stream.read());
                            U.append((char)stream.read());
                            U.append((char)stream.read());
                            U.append((char)stream.read());
                            U.append((char)stream.read());
                            int cp = Integer.parseInt(U.toString(), 16);

                            if (!Character.isValidCodePoint(cp)) {
                                throw new FactParseError(U + " is not a valid 24 bit Unicode character", stream.getOffset());
                            }
                            builder.appendCodePoint(cp);
                            break;
                        default:
                            if (current == -1) {
                                throw new FactParseError("End of input before finding end of String", stream.offset);
                            }
                            builder.append((char)current);
                    }
                    current = stream.read();
                }
                else if (current == -1) {
                    throw new FactParseError("End of input before finding end of String", stream.offset);
                }
                else {
                    builder.appendCodePoint(current);
                    current = stream.read();
                }
            }

            String str = builder.toString();
            current = stream.read();


            if (current == START_OF_ARGUMENTS) {
                ArrayList<IValue> arr = new ArrayList<>();
                Map<String,IValue> kwParams = new HashMap<>();

                readFixed(expected, END_OF_ARGUMENTS, arr, kwParams);

                IValue[] result = arr.toArray(new IValue[0]);
                return factory.node(str, result, kwParams);
            }

            return factory.string(str);
        }

        @Deprecated
        /**
         * The reader still supports the old annotation format for bootstrapping reasons and backward compatibility
         * (for a while). The annotations are read as annotations but stored as keyword parameters. For the
         * parse tree type Tree, the `@\loc` annotation is also rewritten to the `.src` keyword field.
         *
         * @param expected
         * @param result
         * @return
         * @throws IOException
         */
        private IValue readAnnos(Type expected, INode result) throws IOException {
            current = stream.read();

            while (current != ']') {
                checkAndRead('@');
                String key = readIdentifier();

                if (isLegacyParseTreeSourceAnnotation(key, result.getType())) {
                    key = "src";
                }

                checkAndRead('=');

                Type annoType = getAnnoType(expected, key);
                IValue value = readValue(annoType);

                result = result.asWithKeywordParameters().setParameter(key, value);
                if (current == ']') {
                    current = stream.read();
                    break;
                }
                checkAndRead(',');
            }

            return result;
        }

        private boolean isLegacyParseTreeSourceAnnotation(String key, Type type) {
            return key.equals("loc") && type.isAbstractData() && type.getName().equals("Tree");
        }

        private Type getAnnoType(Type expected, String key) {
            Type annoType = null;

            if (expected.isStrictSubtypeOf(TF.nodeType())) {
                if (store.hasKeywordParameter(expected, key)) {
                    annoType = store.getKeywordParameterType(expected, key);
                }
            }

            return annoType != null ? annoType : types.valueType();
        }

        private void readFixed(Type expected, char end, List<@NonNull IValue> arr, Map<String,IValue> kwParams) throws IOException {
            current = stream.read();

            for (int i = 0; current != end; i++) {
                Type exp = expected.isFixedWidth() && i < expected.getArity() ? expected.getFieldType(i) : types.valueType();
                IValue elem = readValue(exp);

                if (current == '=') {
                    String label = ((IString) elem).getValue();
                    current = stream.read();
                    if (expected.isConstructor() && expected.hasField(label)) {
                        kwParams.put(label, readValue(expected.getFieldType(label)));
                    }
                    else {
                        kwParams.put(label, readValue(types.valueType()));
                    }
                }
                else {
                    arr.add(elem);
                }

                if (current != ',' || current == end) {
                    break; // no more elements, so expecting a 'end', or '='
                }
                current = stream.read();
            }

            checkAndRead(end);
        }

        private IValue readContainer(Type elemType, IWriter<?> w, char end) throws FactTypeUseException, IOException {
            current = stream.read();
            while(current != end) {
                w.insert(readValue(elemType));

                if (current != ',' || current == end) {
                    break; // no more elements, so expecting a '}'
                }
                current = stream.read();
            }

            checkAndRead(end);

            return w.done();
        }

        private IValue readList(Type elemType, IListWriter w, char end) throws FactTypeUseException, IOException {
            current = stream.read();
            while (current != end) {
                w.append(readValue(elemType));

                if (current != ',' || current == end) {
                    break; // no more elements, so expecting a '}'
                }
                current = stream.read();
            }

            checkAndRead(end);

            return w.done();
        }

        private void checkAndRead(char c) throws IOException {
            if (current != c) {
                throw unexpectedException((int) c);
            }
            current = stream.read();
        }

        private FactParseError unexpectedException(int c) {
            return new FactParseError("Expected " + ((char) c) + " but got " + ((char) current), stream.getOffset());
        }

        private FactParseError unexpectedException() {
            return new FactParseError("Unexpected " + ((char) current), stream.getOffset());
        }

        private static class NoWhiteSpaceReader extends Reader {
            private Reader wrapped;
            int offset;
            boolean inString = false;
            boolean escaping = false;

            public NoWhiteSpaceReader(Reader wrapped) {
                this.wrapped = wrapped;
            }

            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public int read() throws IOException {
                int r = wrapped.read();
                offset++;

                if (!inString) {
                    while (Character.isWhitespace(r)) {
                        offset++;
                        r = wrapped.read();
                    }
                }

                if (!inString && r == '\"') {
                    inString = true;
                }
                else if (inString) {
                    if (escaping) {
                        // previous was escaping, so no interpretation of current char.
                        escaping = false;
                    }
                    else if (r == '\\') {
                        escaping = true;
                    }
                    else if (r == '"') {
                        // if we were not escaped, a double quote exits a string
                        inString = false;
                    }
                }

                return r;
            }

            int getOffset() {
                return offset;
            }

            @Override
            public void close() throws IOException {
                wrapped.close();
            }
        }

        private static class DateParts {
            private final int year;
            private final int month;
            private final int day;

            public DateParts(int year, int month, int day) {
                this.year = year;
                this.month = month;
                this.day = day;
            }

            public int getYear() {
                return year;
            }

            public int getMonth() {
                return month;
            }

            public int getDay() {
                return day;
            }

            public String toString() {
                return String.format("%04d", year) + "-" + String.format("%02d", month) + "-" + String.format("%02d", day);
            }
        }

        private static class TimeParts {
            private final int hour;
            private final int minute;
            private final int second;
            private final int millisecond;
            private final int timezoneHours;
            private final int timezoneMinutes;

            public TimeParts(int hour, int minute, int second, int millisecond, int timezoneHours, int timezoneMinutes) {
                this.hour = hour;
                this.minute = minute;
                this.second = second;
                this.millisecond = millisecond;
                this.timezoneHours = timezoneHours;
                this.timezoneMinutes = timezoneMinutes;
            }

            public int getHour() {
                return hour;
            }

            public int getMinute() {
                return minute;
            }

            public int getSecond() {
                return second;
            }

            public int getMillisecond() {
                return millisecond;
            }

            public int getTimezoneHours() {
                return timezoneHours;
            }

            public int getTimezoneMinutes() {
                return timezoneMinutes;
            }

            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("%02d",hour)).append(":").append(String.format("%02d",minute)).append(":").append(String.format("%02d",second)).append(".").append(String.format("%03d",millisecond));
                if (timezoneHours < 0 || (timezoneHours == 0 && timezoneMinutes < 0)) {
                    sb.append("-");
                } else {
                    sb.append("+");
                }
                sb.append(String.format("%02d",Math.abs(timezoneHours))).append(":").append(String.format("%02d",Math.abs(timezoneMinutes)));
                return sb.toString();
            }
        }
    }
}
