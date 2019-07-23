// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.potelo.prelude.hitfield;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a highlighted range in a highlighted field.
 */
public class HighlightedRange
{

    public HighlightedRange(int offset, Integer length)
    {
        _offset = offset;
        _length = length;
    }

    public void setLength(Integer length)
    {
        _length = length;
    }

    public int getOffset()
    {
        return _offset;
    }

    public Integer getLength()
    {
        return _length;
    }

    public JSONObject asJSONObject()
    {
        JSONObject json = new JSONObject();
        try
        {
            json.put("offset", _offset);
            json.put("length", _length);
            // TODO category
            return json;
        }
        catch (JSONException e)
        {
            return null;
        }
    }

    private int _offset;

    private Integer _length;

    // TODO category

}
