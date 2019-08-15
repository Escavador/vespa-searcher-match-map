# vespa.ai match map plugin
This project is a plugin for [vespa.ai](https://vespa.ai/) search engine that includes a new Searcher capable of creating a map of matches for your analyzed string, which can show the locations of a query in the text.

## Installation

```bash
mvn install package -f vespa-searcher-match-map/
```

On your search definition file, please include `snip` as a query command in your fields that are going to be mapped. `snip` is incompatible with Vespa's dyn summarization (`summary: dynamic`) and requires a highlight configuration (`bolding: on`).

In orther to retrieve a dyn summarization, please include `dynsnip` as a query command too.

```
field field_name type string {
    indexing: summary | index
    bolding: on
    query-command: snip
    query-command: dynsnip
}
```

On your *services.xml* file, add the searcher (`com.potelo.prelude.searcher.SnipperSearcher`) into a search chain:

```xml
<?xml version="1.0" encoding="utf-8" ?>
<services version="1.0">
    <admin version="2.0">
      <adminserver hostalias="node1" />
    </admin>

    <container version="1.0">
        <search>
          <chain id="default" inherits="vespa">
            <searcher id="com.potelo.prelude.searcher.SnipperSearcher" />
          </chain>
        </search>
        <nodes>
          <node hostalias="node1" />
        </nodes>
    </container>
</services>
```

## Output
```json
{
   "snippets": {
       "field_name": {
           "fieldsnippets": [
               {
                   "offset": 0,
                   "length": 45, 
                   "highlightedranges": [
                       {
                           "offset": 6, 
                           "length": 3
                       }
                   ], 
                   "content": "and a car run in the road with no speed limit"
               },
           ],
           "fieldlength": 1000
      }
   } 
}
```

## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.

## License
[GPL v3](https://www.gnu.org/licenses/gpl-3.0.html)

## Authors and acknowledgement
This project has received contributions of code from
(alphabetically by first name or nickname):

  - Caio Jordão Carvalho ([cjlcarvalho](https://github.com/cjlcarvalho))
  - Thalles Yan Santos Medrado ([tysm](https://github.com/tysm))

