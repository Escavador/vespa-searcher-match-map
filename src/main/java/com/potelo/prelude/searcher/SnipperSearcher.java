package com.potelo.prelude.searcher;

import com.google.inject.Inject;
import com.potelo.prelude.hitfield.HighlightedRange;
import com.potelo.prelude.hitfield.Snippet;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.hitfield.HitField;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.br.BrazilianAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.uhighlight.DefaultPassageFormatter;
import org.apache.lucene.search.uhighlight.PassageFormatter;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

/**
 * Generates a list of Snippets that contains highlighted terms
 * for each configured field.
 * <p>
 * Note: This searchers gathers information over the position of backend binary
 * highlighting tags in highlighted fields. Based on JuniperSearcher.
 */
@After(com.yahoo.prelude.searcher.JuniperSearcher.JUNIPER_TAG_REPLACING)
@Provides(com.potelo.prelude.searcher.SnipperSearcher.HIGHLIGHT_SNIPPETING)
public class SnipperSearcher extends Searcher
{

    @Inject
    public SnipperSearcher(ComponentId id, QrSearchersConfig config)
    {
        super(id);

        // TODO move to configuration files.
        _indexPath = "indexedFiles";
        _lowerBoundSnippetLength = 280;
        _upperBoundSnippetLength = 320;

        _boldOpenTag = config.tag().bold().open();
        _boldCloseTag = config.tag().bold().close();
        _separatorTag = config.tag().separator();
    }

    /**
     * Produce, for each configured field of a Hit, a List of Snippets
     * based on the position of the Juniper highlighting tags..getIndex(fieldname).hasCommand("foo");
     */
    @Override
    public Result search(Query query, Execution execution)
    {
        Result result = execution.search(query); // get results from previous components in the chain.

        String queryVespa = query.getModel().getQueryString();
        boolean isBolding = query.getPresentation().getBolding();
        Iterator<Hit> hitsToProcess = result.hits().deepIterator();
        IndexFacts indexFacts = execution.context().getIndexFacts();

        if (indexFacts != null)
            processHits(queryVespa, isBolding, hitsToProcess, null, indexFacts.newSession(query));

        return result;
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution)
    {
        int worstCase = result.getHitCount();
        List<Hit> hits = new ArrayList<>(worstCase);
        for (Iterator<Hit> i = result.hits().deepIterator(); i.hasNext();)
        {
            Hit sniffHit = i.next();
            if ( ! (sniffHit instanceof FastHit)) continue;

            FastHit hit = (FastHit) sniffHit;
            if (hit.isFilled(summaryClass)) continue;

            hits.add(hit);
        }
        execution.fill(result, summaryClass);

        boolean isBolding = result.getQuery().getPresentation().getBolding();
        Iterator<Hit> hitIterator = hits.iterator();
        IndexFacts indexFacts = execution.context().getIndexFacts();

        if (indexFacts != null)
            processHits(null, isBolding, hitIterator, summaryClass, indexFacts.newSession(result.getQuery()));
    }

    private void processHits(String queryVespa, boolean isBolding, Iterator<Hit> hitsToProcess,
                             String summaryClass, IndexFacts.Session indexFacts)
    {
        while (hitsToProcess.hasNext())
        {
            Hit hit = hitsToProcess.next();

            // snippets must be a reserved word in the search definitions settings.
            Object previousFieldValue = hit.getField("snippets");
            assert(previousFieldValue == null);

            if ( ! (hit instanceof FastHit)) continue;

            FastHit fastHit = (FastHit) hit;
            if (summaryClass != null &&  ! fastHit.isFilled(summaryClass)) continue;

            Object searchDefinitionField = fastHit.getField(_MAGIC_FIELD);
            if (searchDefinitionField == null) continue;

            JSONObject snippets = new JSONObject();
            for (Index index : indexFacts.getIndexes(searchDefinitionField.toString()))
            {
                // snip and dynsnip are incompatibles with Vespa's dyn summary.
                // both snip and dynsnip needs a highlight configuration (bolding).
                if (index.getDynamicSummary() || ! index.getHighlightSummary())
                    continue;

                HitField field;
                if (index.hasCommand("snip") || index.hasCommand("dynsnip"))
                {
                    field = fastHit.buildHitField(index.getName(), true);
                    if (field == null)
                        continue;
                }
                else
                    continue;

                String documentToProcess = field.getContent();
                documentToProcess = documentToProcess.replaceAll(Character.toString(UtilsSearcher.RAW_HIGHLIGHT_CHAR), "");
                documentToProcess = documentToProcess.replaceAll(Character.toString(UtilsSearcher.RAW_SEPARATOR_CHAR), "");

                if (index.hasCommand("snip"))
                {
                    JSONArray fieldSnippets = null;

                    List<HighlightedRange> hiRanges = UtilsSearcher.gatherHiPositions(field, isBolding);
                    if (hiRanges != null)
                    {
                        try
                        {
                            fieldSnippets = generateSnippets(documentToProcess, hiRanges);
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                    }

                    if (fieldSnippets != null) {
                        if (fieldSnippets.length() == 0)
                            fieldSnippets = null;
                        else
                            fieldSnippets = UtilsSearcher.<Integer>sortJsonArray(fieldSnippets, "offset");
                    }

                    JSONObject fieldInfo = new JSONObject();
                    try
                    {
                        fieldInfo.put("fieldlength", documentToProcess.length());
                        fieldInfo.put("fieldsnippets", fieldSnippets);

                        snippets.put(index.getName(), fieldInfo);
                    }
                    catch (JSONException e)
                    {
                        e.printStackTrace();
                    }
                }

                if (index.hasCommand("dynsnip"))
                {
                    String dynsnippet = generateDynamicSnippet(queryVespa, documentToProcess);
                    if (dynsnippet == null)
                    {
                        int rightOffset = documentToProcess.length();

                        List<Integer> tokEndPositions = UtilsSearcher.gatherPatternPositions("(?<=\\w)\\b", documentToProcess);
                        if (tokEndPositions == null)
                            rightOffset = Math.min(rightOffset, _upperBoundSnippetLength);
                        else
                        {
                            int i = UtilsSearcher.lowerBound(tokEndPositions, _upperBoundSnippetLength);
                            if (i == tokEndPositions.size() || _upperBoundSnippetLength < tokEndPositions.get(i))
                                i--;
                            rightOffset = Math.min(rightOffset, tokEndPositions.get(i));
                        }

                        dynsnippet = documentToProcess.substring(0, rightOffset);
                        if (dynsnippet.length() < documentToProcess.length())
                            dynsnippet += _separatorTag;
                    }
                    hit.setField(index.getName(), dynsnippet);
                }
            }
            // snippets must be a reserved word in the search definitions settings.
            if (snippets.length() > 0 )
                hit.setField("snippets", snippets);
        }
    }

    private String generateDynamicSnippet(String queryVespa, String documentToProcess)
    {
        try
        {
            // index dir.
            Directory dir = MMapDirectory.open(Paths.get(_indexPath));

            // analyzer with the default stop words.
            Analyzer analyzer = new BrazilianAnalyzer();

            // index writer Configuration.
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

            // index writer.
            IndexWriter iw = new IndexWriter(dir, iwc);

            // the document that we need to process.
            Document doc = new Document();
            doc.add(new StringField("toProcess", documentToProcess, Field.Store.YES));

            // adding the document.
            iw.addDocument(doc);
            iw.commit();

            // index reader and lucene's searcher.
            IndexReader ir = DirectoryReader.open(iw);
            IndexSearcher searcher = new IndexSearcher(ir);

            // setting the highlighter.
            PassageFormatter formatter = new DefaultPassageFormatter(_boldOpenTag, _boldCloseTag, _separatorTag, false);
            UnifiedHighlighter highlighter = new UnifiedHighlighter(searcher, analyzer);
            highlighter.setFormatter(formatter);
            highlighter.setMaxLength(_upperBoundSnippetLength);

            QueryParser qp = new QueryParser("toProcess", analyzer);
            org.apache.lucene.search.Query luceneQuery = qp.parse(String.format("toProcess: \"%s\"", queryVespa));

            Map<String, String[]> snippetsMap = highlighter.highlightFields(new String[] {"toProcess"}, luceneQuery, new int[] {0}, new int[] {1});
            String[] snippets = snippetsMap.get("toProcess");
            if (snippets.length == 0)
                return null;
            String snippet = snippets[0];

            // closing the reader.
            ir.close();

            // deleting all docs in this index and closing this writer.
            iw.deleteAll();
            iw.commit();
            iw.close();

            // closing the dir.
            dir.close();

            return snippet;
        }
        catch (IOException | ParseException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    private JSONArray generateSnippets(String documentToProcess, List<HighlightedRange> hiRanges) throws Exception
    {
        // here we assume a non null and non empty and ordered list of highlighted ranges!

        List<Snippet> snippets = new ArrayList<>();

        if (hiRanges.size() == 1)
        {
            HighlightedRange hiRange = hiRanges.get(0);
            String content = documentToProcess.substring(hiRange.getOffset(), hiRange.getOffset() + hiRange.getLength());

            Snippet snippet = new Snippet(hiRange.getOffset(), content);

            snippet.addHighlightedRange(hiRange);

            snippets.add(snippet);
        }
        else
        {
            PriorityQueue<List<Integer>> queue = new PriorityQueue<>(hiRanges.size(), new UtilsSearcher.MinListComparator());
            for (int i = 1; i < hiRanges.size(); ++i)
            {
                HighlightedRange fstHiRange = hiRanges.get(i - 1), sndHiRange = hiRanges.get(i);
                int dist = sndHiRange.getOffset() - (fstHiRange.getOffset() + fstHiRange.getLength());
                queue.add(new ArrayList<>(Arrays.asList(dist, fstHiRange.getOffset(), i - 1, i)));
            }

            Map<HighlightedRange, Snippet> hiRange2Snippet = new HashMap<>();
            while (!queue.isEmpty())
            {
                List<Integer> list = queue.remove();
                HighlightedRange fstHiRange = hiRanges.get(list.get(2));
                HighlightedRange sndHiRange = hiRanges.get(list.get(3));

                Snippet snippet1 = hiRange2Snippet.get(fstHiRange);
                Snippet snippet2 = hiRange2Snippet.get(sndHiRange);

                if (snippet1 != null && snippet2 != null)
                {
                    int offset = snippet1.getOffset() + snippet1.length();
                    int length = ((snippet2.getOffset() + snippet2.length()) - offset);

                    // we should not merge if the new length pass the optimal value.
                    if (snippet1.length() + length <= _upperBoundSnippetLength)
                    {
                        String content = documentToProcess.substring(offset, offset + length);
                        snippet1.pushContentBack(content);

                        for (HighlightedRange hiRange : snippet2.getHighlightedRanges())
                        {
                            snippet1.addHighlightedRange(hiRange);
                            hiRange2Snippet.put(hiRange, snippet1);
                        }
                        snippets.remove(snippet2);
                    }
                }
                else if (snippet1 != null)
                {
                    int offset = snippet1.getOffset() + snippet1.length();
                    int length = ((sndHiRange.getOffset() + sndHiRange.getLength()) - offset);

                    // we should not merge if the new length pass the optimal value.
                    if (snippet1.length() + length < _upperBoundSnippetLength)
                    {
                        String content = documentToProcess.substring(offset, offset + length);
                        snippet1.pushContentBack(content);

                        snippet1.addHighlightedRange(sndHiRange);
                        hiRange2Snippet.put(sndHiRange, snippet1);
                    }
                    else
                    {
                        offset = sndHiRange.getOffset();
                        length = sndHiRange.getLength();
                        String content = documentToProcess.substring(offset, offset + length);

                        snippet2 = new Snippet(offset, content);

                        snippet2.addHighlightedRange(sndHiRange);
                        hiRange2Snippet.put(sndHiRange, snippet2);

                        snippets.add(snippet2);
                    }
                }
                else if (snippet2 != null)
                {
                    int offset = fstHiRange.getOffset();
                    int length = snippet2.getOffset() - offset;

                    // we should not merge if the new length pass the optimal value.
                    if (snippet2.length() + length < _upperBoundSnippetLength)
                    {
                        String content = documentToProcess.substring(offset, offset + length);
                        snippet2.pushContentFront(content);

                        snippet2.addHighlightedRange(fstHiRange);
                        hiRange2Snippet.put(fstHiRange, snippet2);
                    }
                    else
                    {
                        length = fstHiRange.getLength();
                        String content = documentToProcess.substring(offset, offset + length);

                        snippet1 = new Snippet(offset, content);

                        snippet1.addHighlightedRange(fstHiRange);
                        hiRange2Snippet.put(fstHiRange, snippet1);

                        snippets.add(snippet1);
                    }
                }
                else
                {
                    int offset = fstHiRange.getOffset();
                    int length = (sndHiRange.getOffset() + sndHiRange.getLength()) - offset;

                    // we should not merge if the new length pass the optimal value.
                    if (length < _upperBoundSnippetLength)
                    {
                        String content = documentToProcess.substring(offset, offset + length);
                        Snippet snippet = new Snippet(offset, content);

                        snippet.addHighlightedRange(fstHiRange);
                        hiRange2Snippet.put(fstHiRange, snippet);

                        snippet.addHighlightedRange(sndHiRange);
                        hiRange2Snippet.put(sndHiRange, snippet);

                        snippets.add(snippet);
                    }
                    else
                    {
                        offset = fstHiRange.getOffset();
                        length = fstHiRange.getLength();
                        String content = documentToProcess.substring(offset, offset + length);

                        snippet1 = new Snippet(offset, content);

                        snippet1.addHighlightedRange(fstHiRange);
                        hiRange2Snippet.put(fstHiRange, snippet1);
                        snippets.add(snippet1);

                        offset = sndHiRange.getOffset();
                        length = sndHiRange.getLength();
                        content = documentToProcess.substring(offset, offset + length);

                        snippet2 = new Snippet(offset, content);

                        snippet2.addHighlightedRange(sndHiRange);
                        hiRange2Snippet.put(sndHiRange, snippet2);
                        snippets.add(snippet2);
                    }
                }
            }
        }

        List<Integer> tokStartPositions = UtilsSearcher.gatherPatternPositions("\\b(?=\\w)", documentToProcess);
        List<Integer> tokEndPositions = UtilsSearcher.gatherPatternPositions("(?<=\\w)\\b", documentToProcess);
        if (tokStartPositions == null || tokEndPositions == null)
            return null;
        List<Integer> hiRangeStartPositions = new ArrayList<>();
        List<Integer> hiRangeEndPositions = new ArrayList<>();
        for (HighlightedRange hiRange : hiRanges)
        {
            hiRangeStartPositions.add(hiRange.getOffset());
            hiRangeEndPositions.add(hiRange.getOffset() + hiRange.getLength());
        }

        JSONArray jsonArray = new JSONArray();
        for (Snippet snippet : snippets)
        {
            if (snippet.length() < _lowerBoundSnippetLength)
            {
                int originalLeftOffset = snippet.getOffset();
                int originalRightOffset = snippet.getOffset() + snippet.length();

                int snippetLack = _upperBoundSnippetLength - snippet.length();

                // trying to grow left half snippetLack.
                int i = UtilsSearcher.lowerBound(tokStartPositions, snippet.getOffset() - snippetLack / 2);
                int offset = Math.min(tokStartPositions.get(i), snippet.getOffset());

                String content = documentToProcess.substring(offset, snippet.getOffset());
                snippet.pushContentFront(content);
                snippetLack -= content.length();

                // trying to grow right the rest of snippetLack.
                offset = snippet.getOffset() + snippet.length();
                i = UtilsSearcher.lowerBound(tokEndPositions, offset + snippetLack);
                if (i == tokEndPositions.size() || offset + snippetLack < tokEndPositions.get(i))
                    i--;  // the lower bound may be greater than we expect.

                content = documentToProcess.substring(offset, Math.max(offset, tokEndPositions.get(i)));
                snippet.pushContentBack(content);
                snippetLack -= content.length();

                // trying to grow left again the rest of snippetLack.
                i = UtilsSearcher.lowerBound(tokStartPositions, snippet.getOffset() - snippetLack);
                offset = Math.min(tokStartPositions.get(i), snippet.getOffset());

                content = documentToProcess.substring(offset, snippet.getOffset());
                snippet.pushContentFront(content);

                // searching for hiRanges in the new snippet left range.
                if (snippet.getOffset() < originalLeftOffset)
                {
                    i = UtilsSearcher.lowerBound(hiRangeStartPositions, snippet.getOffset());
                    if (hiRangeStartPositions.get(i) < originalLeftOffset)
                    {
                        int j = UtilsSearcher.lowerBound(hiRangeEndPositions, originalLeftOffset);
                        if (originalLeftOffset < hiRangeEndPositions.get(j))
                            j--;
                        for (; i <= j; ++i)
                            snippet.addHighlightedRange(hiRanges.get(i));
                    }
                }
                if (originalRightOffset < snippet.getOffset() + snippet.length())
                {
                    i = UtilsSearcher.lowerBound(hiRangeStartPositions, originalRightOffset);
                    if (i < hiRangeStartPositions.size())
                    {
                        int j = UtilsSearcher.lowerBound(hiRangeEndPositions, snippet.getOffset() + snippet.length());
                        if (j == hiRangeEndPositions.size()
                                || snippet.getOffset() + snippet.length() < hiRangeEndPositions.get(j))
                            j--;
                        for (; i <= j; ++i)
                            snippet.addHighlightedRange(hiRanges.get(i));
                    }
                }
            }
            jsonArray.put(snippet.asJSONObject());
        }
        return jsonArray;
    }

    static final String HIGHLIGHT_SNIPPETING = "HighlightSnippeting";

    // The name of the field containing document type
    private static final String _MAGIC_FIELD = Hit.SDDOCNAME_FIELD;

    // TODO move to configuration files.
    private String _indexPath;
    private int _lowerBoundSnippetLength; // lower optimal snippet length.
    private int _upperBoundSnippetLength; // upper optimal snippet length.

    private String _boldOpenTag;
    private String _boldCloseTag;
    private String _separatorTag;
}
