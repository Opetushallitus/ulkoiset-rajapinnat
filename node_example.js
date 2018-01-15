const http = require('http')
const fs = require('fs')
const request = require('request')
const cheerio = require('cheerio')
const Promise = require('bluebird')

const [file_with_creds_and_host] = process.argv.slice(-1)
const [username, password, hostname] = fs.readFileSync(file_with_creds_and_host, 'utf8').split(/\r?\n/)

const postForm = (url, form) => {
	return new Promise((resolve, reject) => {
		request.post(url, {form:form}, (error, response, body) => {
			if(error) {
				reject(error)
			} else {
				resolve(body)
			}
		})
	})
}

const ticketGrantingTicketFromResponseHtml = (htmlPageWithTicket) => {
	$ = cheerio.load(htmlPageWithTicket)
	const tgt = $('form').attr('action')
	return tgt
}

postForm(hostname + '/cas/v1/tickets', {username:username, password:password})
	.then(ticketGrantingTicketFromResponseHtml)
	.then((tgtUrl) => postForm(tgtUrl,{service:hostname+'/ulkoiset-rajapinnat'}))
	.then((serviceTicket) => {
		const url = hostname + '/ulkoiset-rajapinnat/api/haku-for-year/2001?ticket=' + serviceTicket
                console.log('Calling URL ' + url)
		request.get(url, (e,r,body) => {
                        console.log(body)
                })		
	})
