package com.fasterxml.jackson.core.sym;

import java.util.*;

import com.fasterxml.jackson.core.util.Named;

public final class SimpleCSNameMatcher
    extends FieldNameMatcher
    implements java.io.Serializable
{
    private static final long serialVersionUID = 1L;

    private final String[] _names;
    private final int[] _offsets;

    private final int _mask;

    private SimpleCSNameMatcher(String[] names, int[] offsets, int mask) {
        _names = names;
        _offsets = offsets;
        _mask = mask;
    }

    public static FieldNameMatcher construct(List<Named> fields)
    {
        int size = fields.size();
        if (size <= 4) {
            return Small.construct(fields);
        }

        // First: calculate size of primary hash area
        final int hashSize = findSize(size);
        final int mask = hashSize-1;

        // primary is hashSize; secondary hashSize/2; spill-overs after that
        String[] names = new String[hashSize + (hashSize>>1)];
        int[] offsets = new int[names.length];
        int spillPtr = names.length;
        for (int i = 0; i < size; ++i) {
            Named n = fields.get(i);
            // 11-Nov-2017, tatu: Holes are actually allowed -- odd but true
            if (n == null) {
                continue;
            }
            String name = n.getName();
            int ix = name.hashCode() & mask;
            if (names[ix] == null) {
                names[ix] = name;
                offsets[ix] = i;
                continue;
            }
            // secondary?
            ix = (mask+1) + (ix >> 1);
            if (names[ix] == null) {
                names[ix] = name;
                offsets[ix] = i;
                continue;
            }

            // no, spill-over
            if (names.length >= spillPtr) {
                int newLength = names.length + 4;
                names = Arrays.copyOf(names, newLength);
                offsets = Arrays.copyOf(offsets, newLength);
            }
            names[spillPtr] = name;
            offsets[spillPtr] = i;
            ++spillPtr;
        }
        return new SimpleCSNameMatcher(names, offsets, mask);
    }

    private final static int findSize(int size) {
        if (size <= 3) return 4;
        if (size <= 6) return 8;
        if (size <= 12) return 16;
        int needed = size + (size >> 2); // at most 80% full
        int result = 32;
        while (result < needed) {
            result += result;
        }
        return result;
    }
    
    @Override
    public int matchName(String toMatch) {
        int ix = toMatch.hashCode() & _mask;
        String name = _names[ix];
        if ((toMatch == name) || toMatch.equals(name)) {
            return _offsets[ix];
        }
        if (name != null) {
            // check secondary slot
            ix = (_mask + 1) + (ix >> 1);
            name = _names[ix];
            if (toMatch.equals(name)) {
                return _offsets[ix];
            }
            // or spill-over if need be
            if (name != null) {
                return _matchSpill(toMatch);
            }
        }
        return MATCH_UNKNOWN_NAME;
    }

    private final int _matchSpill(String toMatch) {
        int ix = (_mask+1);
        ix += (ix>>1);

        for (int end = _names.length; ix < end; ++ix) {
            String name = _names[ix];
            if (toMatch.equals(name)) {
                return _offsets[ix];
            }
            if (name == null) {
                break;
            }
        }
        return MATCH_UNKNOWN_NAME;
    }

    /*
    /**********************************************************************
    /* Specialized matcher for small number of fields
    /**********************************************************************
     */

    private final static class Small extends FieldNameMatcher
        implements java.io.Serializable
    {
        private static final long serialVersionUID = 1L;

        protected final String a, b, c, d;

        private Small(String f1, String f2, String f3, String f4) {
            a = f1;
            b = f2;
            c = f3;
            d = f4;
        }

        public static Small construct(List<Named> fields) {
                return new Small(
                    _get(fields, 0),
                    _get(fields, 1),
                    _get(fields, 2),
                    _get(fields, 3));
        }

        private static String _get(List<Named> fields, int index) {
            if (index < fields.size()) {
                Named n = fields.get(index);
                if (n != null) {
                    return n.getName();
                }
            }
            return null;
        }

        @Override
        public int matchName(String name) {
            if (name.equals(a)) {
                return 0;
            }
            if (name.equals(b)) {
                return 1;
            }
            if (name.equals(c)) {
                return 2;
            }
            if (name.equals(d)) {
                return 3;
            }
            return FieldNameMatcher.MATCH_UNKNOWN_NAME;
        }
    }
}
