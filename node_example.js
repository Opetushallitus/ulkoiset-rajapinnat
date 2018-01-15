const http = require('http')
const fs = require('fs')
const querystring = require('querystring')
const request = require('request')
const cheerio = require('cheerio')

const [file_with_creds_and_host] = process.argv.slice(-1)
const [username, password, hostname] = fs.readFileSync(file_with_creds_and_host, 'utf8').split(/\r?\n/)

request.post(hostname + '/cas/v1/tickets', {form:{username:username, password:password}}, (error,response,htmlPageWithTicket) => {
	$ = cheerio.load(htmlPageWithTicket)
	const ticket = $('form').attr('action')

	const url = hostname + '/ulkoiset-rajapinnat/api/haku-for-year/2001?ticket=' + ticket
	console.log('Calling URL ' + url)
	request.get(url, (e,r,body) => {
		console.log(body)
	})
})


