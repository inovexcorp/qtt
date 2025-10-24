<#ftl output_format="plainText" encoding="UTF-8" strict_syntax=false auto_esc=false strip_whitespace=true>
<#--noinspection ALL-->
####################################################
# Templated At: ${.now?iso_local_ms}
# Resource: ${headers.CamelHttpUri}
####################################################
PREFIX : <http://cambridgesemantics.com/ontologies/OpenSky#>
CONSTRUCT {
?vector ?pred ?object .
}
WHERE {
    {
        SELECT ?vector
        WHERE {
            ?vector a :State_Vector ;
                :p_Origin_Country "${headers.originCountry}" .
        }
        LIMIT ${headers.pageSize!'10'}
    }
    ?vector ?pred ?object .
}
