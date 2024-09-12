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
## hakemus-for-haku api (Tilastokeskus)

Toisen asteen yhteishaun oidilla tulee aikakatkaisu clientilta ennen responsea, joten tiedot pitää ladata cacheen ennenkuin tilastokeskus hakee ne.
Tämä tapahtuu /hakemus-for-haku-job/ apilla ja kestää useita tunteja. Tähän voi käyttää node-clientia.

Jos apia käytetään vielä ennen uuden tietovaraston valmistumista, täytyy synkata Tilastokeskuksen kanssa että tiedot ovat saatavilla cachessa kun he hakevat niitä.

## License

EUPL
