package com.potelo.prelude.searcher;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;
import com.yahoo.search.result.Hit;

import java.nio.charset.StandardCharsets;

import static java.net.URLEncoder.encode;

class SnipperSearcherTest {

    @Test
    void test() {

        Query query = new Query("/search/?yql=" +
                                encode("SELECT * FROM SOURCES teste WHERE userQuery();",
                                StandardCharsets.UTF_8) + "&query=query_string");

        QrSearchersConfig.Builder builder = new QrSearchersConfig.Builder();

        SnipperSearcher searcher = new SnipperSearcher(new ComponentId("test"), builder.build());

        Chain<Searcher> myChain = new Chain<>(searcher);  // added to chain in this order
        Execution.Context context = Execution.Context.createContextStub();
        Execution execution = new Execution(myChain, context);

        Result result = execution.search(query);
    }

}