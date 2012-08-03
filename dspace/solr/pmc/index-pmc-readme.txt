wget ftp://ftp.ncbi.nlm.nih.gov/pub/pmc/PMC-ids.csv.gz

userdspace@localmachine:curl "http://localhost:8080/solr/pmc/update/csv?stream.file=\PMC-ids.csv&stream.contentType=text/plain;charset=utf-8&commit=true&optimize=true&fieldnames=,,,,,,,DOI,PMCID,PMID,,&skipLines=1"
or
userdspace@localmachine:/{dspace.dir}/solr/pmc$ wget "http://localhost:8080/solr/pmc/update/csv?stream.file=//{dspace.dir}/pubmed/PMC-ids.csv&stream.contentType=text/plain;charset=utf-8&commit=true&optimize=true&fieldnames=,,,,,,,DOI,PMCID,PMID,,&skipLines=1"
