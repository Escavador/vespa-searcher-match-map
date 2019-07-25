// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.potelo.prelude.hitfield;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a range of String content in a highlighted field.
 */
public class Snippet
{

    public Snippet(int offset, String content)
    {
        _offset = offset;
        _content = content;
        _highlightedRanges = new ArrayList<>();
    }

    public int getOffset()
    {
        return _offset;
    }

    public String getContent()
    {
        return _content;
    }

    public List<HighlightedRange> getHighlightedRanges()
    {
        return _highlightedRanges;
    }

    public void pushContentFront(String content) throws Exception
    {
        if (content == null)
            throw new Exception("Can't add null content");
        else if (_content == null)
            _content = content;
        else {
            _content = content + _content;
            _offset -= content.length();
            if (_offset < 0)
                throw new Exception("Offset is negative");
        }
    }

    public void pushContentBack(String content) throws Exception
    {
        if (content == null)
            throw new Exception("Can't add null content");
        else if (_content == null)
            _content = content;
        else
            _content = _content + content;
    }

    public void addHighlightedRange(HighlightedRange highlightedRange) throws Exception
    {
        if (highlightedRange == null)
            throw new Exception("Can't add null range");
        else if (highlightedRange.getLength() == null)
            throw new Exception("Can't add undefined range");
        else if (_offset > highlightedRange.getOffset())
            throw new Exception("Highlighted range is out of bounds");
        else if (_content == null)
            throw new Exception("There's no highlightable content");
        else if (_offset + _content.length() < highlightedRange.getOffset() + highlightedRange.getLength())
            throw new Exception("Highlighted range is out of bounds");
        _highlightedRanges.add(highlightedRange);
    }

    public Integer length()
    {
        return _content == null ? null : _content.length();
    }

    public JSONObject asJSONObject()
    {
        JSONObject json = new JSONObject();
        try
        {
            json.put("offset", _offset);
            // TODO separator
            json.put("content", "..." + _content + "...");
            json.put("length", length());

            JSONArray array = new JSONArray();
            for (HighlightedRange hiRange : _highlightedRanges)
                array.put(hiRange.asJSONObject());
            json.put("highlightedranges", array);

            return json;
        }
        catch (JSONException e)
        {
            return null;
        }
    }

    private int _offset;

    // TODO separator

    private String _content;

    private List<HighlightedRange> _highlightedRanges;

}
