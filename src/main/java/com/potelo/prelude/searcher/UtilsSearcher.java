package com.potelo.prelude.searcher;

import com.potelo.prelude.hitfield.HighlightedRange;
import com.yahoo.prelude.hitfield.HitField;
import com.yahoo.prelude.searcher.JuniperSearcher;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Comparator;

class UtilsSearcher
{

    final static char RAW_HIGHLIGHT_CHAR = JuniperSearcher.RAW_HIGHLIGHT_CHAR;
    final static char RAW_SEPARATOR_CHAR = JuniperSearcher.RAW_SEPARATOR_CHAR;

    static <T> int lowerBound(List<? extends Comparable<? super T>> list, T key)
    {
        int i = Collections.binarySearch(list, key);
        if (i >= 0)
        {
            if (i == 0)
                return i;

            while (i > 0 && list.get(i).equals(key))
                i -= 1;
            return list.get(i).equals(key)? i : i + 1;
        }
        return -(i + 1);
    }

    static List<Integer> gatherPatternPositions(String regex, String text)
    {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        List<Integer> result = new ArrayList<>();
        while (matcher.find())
            result.add(matcher.start());

        if (result.size() == 0)
            return null;
        return result;
    }

    @SuppressWarnings("unchecked")
    static <T extends Comparable> JSONArray sortJsonArray (JSONArray array, String fieldToCompare)
    {
        List<JSONObject> jsonValues = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            try {
                jsonValues.add(array.getJSONObject(i));
            }
            catch (JSONException e) {
                return array;
            }
        }
        jsonValues.sort((JSONObject a, JSONObject b) -> {
            try {
                T offsetA = (T) a.get(fieldToCompare);
                T offsetB = (T) b.get(fieldToCompare);

                return offsetA.compareTo(offsetB);
            }
            catch (JSONException e) {
                return 0;
            }
        });

        JSONArray sortedArray = new JSONArray();

        for (JSONObject jsObj : jsonValues)
            sortedArray.put(jsObj);

        return sortedArray;
    }

    static List<HighlightedRange> gatherHiPositions(HitField field, boolean bolding)
    {
        List<HighlightedRange> highlightedRanges = null;

        int tagCount = 0;
        Character lastChar = null;
        boolean insideHighlight = false;

        String toProcess = field.getContent();
        for (int i = 0; i < toProcess.length(); ++i)
        {
            char key = toProcess.charAt(i);
            switch (key)
            {
            case RAW_HIGHLIGHT_CHAR:
                tagCount++;
                highlightedRanges = highlightedRanges == null ? new ArrayList<>() : highlightedRanges;
                addHiPosition(bolding, insideHighlight, highlightedRanges, i, tagCount, lastChar);
                insideHighlight = !insideHighlight;
                break;
            case RAW_SEPARATOR_CHAR:
                tagCount++;
                break;
            default:
                break;
            }
            lastChar = key;
        }
        return highlightedRanges;
    }

    private static void addHiPosition(boolean bolding, boolean insideHighlight, List<HighlightedRange> highlightedRanges,
                                      int i, int tagCount, Character lastChar)
    {
        if (bolding)
        {
            int rawPos = i - tagCount + 1;

            if (insideHighlight)
            {
                assert(highlightedRanges.size() > 0);  // if insideHighlight... Doesn't make sense to fail.

                HighlightedRange lastPosition = highlightedRanges.get(highlightedRanges.size() - 1);
                lastPosition.setLength(rawPos - lastPosition.getOffset());
            }
            else
            {
                if (lastChar != null && lastChar.equals(RAW_HIGHLIGHT_CHAR))
                {
                    // if not insideHighlight and lastChar == RAW_HIGHLIGHT_CHAR
                    // means that lastChar was a _boldCloseTag and now we're on a
                    // _boldOpenTag, so we should merge these intervals instead of
                    // open a new HighlightedRange since JuniperSearcher does the
                    // same.
                    // Due this case, we just need to reopen the last interval
                    // and let this algorithm close the tag.
                    assert(highlightedRanges.size() > 0);  // if lastChar.equals(RAW_HIGHLIGHT_CHAR)...

                    HighlightedRange lastPosition = highlightedRanges.get(highlightedRanges.size() - 1);
                    lastPosition.setLength(null);
                }
                else
                {
                    // otherwise we just need to open a new _boldOpenTag
                    highlightedRanges.add(new HighlightedRange(rawPos, null));
                }
            }
        }
    }

    public static class MinListComparator implements Comparator<List<Integer>>
    {
        @Override
        public int compare(List<Integer> x, List<Integer> y)
        {
            for (int i = 0; i < Math.min(x.size(), y.size()); ++i)
            {
                if (x.get(i) < y.get(i))
                    return -1;
                else if (x.get(i) > y.get(i))
                    return 1;
            }
            return Integer.compare(x.size(), y.size());
        }
    }

}