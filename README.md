# vespa.ai match map plugin
This project is a plugin for [vespa.ai](https://vespa.ai/) search engine that includes a new Searcher capable of creating a map of matches for your analyzed string, which can show the locations of a query in the text.

## Installation

```bash
mvn install package -f vespa-searcher-match-map/
```

On your search definition file, please include `snip` and `dynsnip` as query commands in your fields that are going to be mapped.

```
field field_name type string {
    indexing: summary | index
    query-command: snip
    query-command: dynsnip
}
```

## Output
```json
{
   ...
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
               ...
           ],
           "fieldlength": 1000
      }
   } 
   ...
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

