# ulkoiset-rajapinnat


## Usage

`lein run`

or

`lein run ulkoisetrajapinnat-properties=../my.edn`

or run in REPL

`(ulkoiset-rajapinnat.core/-main "ulkoisetrajapinnat-properties=my.edn")`

or run Java part only from Idea (Edit Configurations -> Environment Variables -> config=../my.edn)

`RunJavaApi`

## CAS with Node.js Example

`npm install --save cheerio request bluebird`

`node node_example.js ../file_with_creds_and_host.txt`

where `file_with_creds_and_host.txt` contains three lines: username, password, host

```
my_username
my_password
https://my_server
```

## License

EUPL
