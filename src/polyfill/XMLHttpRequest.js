// Copyright 2016 wkh237@github. All rights reserved.
// Use of this source code is governed by a MIT-style license that can be
// found in the LICENSE file.

import RNFetchBlob from '../index.js'
import XMLHttpRequestEventTarget from './XMLHttpRequestEventTarget.js'
import Log from '../utils/log.js'
import Blob from './Blob.js'
import ProgressEvent from './ProgressEvent.js'

const log = new Log('XMLHttpRequest')

log.disable()

const UNSENT = 0
const OPENED = 1
const HEADERS_RECEIVED = 2
const LOADING = 3
const DONE = 4

export default class XMLHttpRequest extends XMLHttpRequestEventTarget{

  _onreadystatechange : () => void;

  upload : XMLHttpRequestEventTarget = new XMLHttpRequestEventTarget();

  // readonly
  _readyState : number = UNSENT;
  _response : any = '';
  _responseText : any = null;
  _responseHeaders : any = {};
  _responseType : '' | 'arraybuffer' | 'blob' | 'document' | 'json' | 'text' = '';
  // TODO : not suppoted ATM
  _responseURL : null = '';
  _responseXML : null = '';
  _status : number = 0;
  _statusText : string = '';
  _timeout : number = 0;
  _sendFlag : boolean = false;
  _uploadStarted : boolean = false;

  // RNFetchBlob compatible data structure
  _config : RNFetchBlobConfig = {};
  _url : any;
  _method : string;
  _headers: any = {
    'Content-Type' : 'text/plain'
  };
  _body: any;

  // RNFetchBlob promise object, which has `progress`, `uploadProgress`, and
  // `cancel` methods.
  _task: any;

  // constants
  get UNSENT() { return UNSENT }
  get OPENED() { return OPENED }
  get HEADERS_RECEIVED() { return HEADERS_RECEIVED }
  get LOADING() { return LOADING }
  get DONE() { return DONE }

  static get UNSENT() {
    return UNSENT
  }

  static get OPENED() {
    return OPENED
  }

  static get HEADERS_RECEIVED() {
    return HEADERS_RECEIVED
  }

  static get LOADING() {
    return LOADING
  }

  static get DONE() {
    return DONE
  }

  constructor() {
    super()
    log.verbose('XMLHttpRequest constructor called')
  }


  /**
   * XMLHttpRequest.open, always async, user and password not supported. When
   * this method invoked, headers should becomes empty again.
   * @param  {string} method Request method
   * @param  {string} url Request URL
   * @param  {true} async Always async
   * @param  {any} user NOT SUPPORTED
   * @param  {any} password NOT SUPPORTED
   */
  open(method:string, url:string, async:true, user:any, password:any) {
    log.verbose('XMLHttpRequest open ', method, url, async, user, password)
    this._method = method
    this._url = url
    this._headers = {}
    this._dispatchReadStateChange(XMLHttpRequest.OPENED)
  }

  /**
   * Invoke this function to send HTTP request, and set body.
   * @param  {any} body Body in RNfetchblob flavor
   */
  send(body) {

    if(this._readyState !== XMLHttpRequest.OPENED)
      throw 'InvalidStateError : XMLHttpRequest is not opened yet.'

    this._sendFlag = true
    log.verbose('XMLHttpRequest send ', body)
    let {_method, _url, _headers } = this
    log.verbose('sending request with args', _method, _url, _headers, body)
    log.verbose(typeof body, body instanceof FormData)

    if(body instanceof Blob) {
      body = RNFetchBlob.wrap(body.getRNFetchBlobRef())
    }
    else if(typeof body === 'object') {
      body = JSON.stringify(body)
    }
    else
      body = body ? body.toString() : body

    this._task = RNFetchBlob
                  .config({ auto: true, timeout : this._timeout })
                  .fetch(_method, _url, _headers, body)
    this.dispatchEvent('load')
    this._task
        .stateChange(this._headerReceived.bind(this))
        .uploadProgress(this._uploadProgressEvent.bind(this))
        .progress(this._progressEvent.bind(this))
        .catch(this._onError.bind(this))
        .then(this._onDone.bind(this))
  }

  overrideMimeType(mime:string) {
    log.verbose('XMLHttpRequest overrideMimeType', mime)
    this._headers['Content-Type'] = mime
  }

  setRequestHeader(name, value) {
    log.verbose('XMLHttpRequest set header', name, value)
    if(this._readyState !== OPENED || this._sendFlag) {
      throw `InvalidStateError : Calling setRequestHeader in wrong state  ${this._readyState}`
    }
    // UNICODE SHOULD NOT PASS
    if(typeof name !== 'string' || /[^\u0000-\u00ff]/.test(name)) {
      throw 'TypeError : header field name should be a string'
    }
    //
    let invalidPatterns = [
      /[\(\)\>\<\@\,\:\\\/\[\]\?\=\}\{\s\ \u007f\;\t\0\v\r]/,
      /tt/
    ]
    for(let i in invalidPatterns) {
      if(invalidPatterns[i].test(name) || typeof name !== 'string') {
        throw `SyntaxError : Invalid header field name ${name}`
      }
    }
    this._headers[name] = value
  }

  abort() {
    log.verbose('XMLHttpRequest abort ')
    if(!this._task)
      return
    this._task.cancel((err) => {
      let e = {
        timeStamp : Date.now(),
      }
      if(this.onabort)
        this.onabort()
      if(err) {
        e.detail = err
        e.type = 'error'
        this.dispatchEvent('error', e)
      }
      else {
        e.type = 'abort'
        this.dispatchEvent('abort', e)
      }
    })
  }

  getResponseHeader(field:string):string | null {
    log.verbose('XMLHttpRequest get header', field)
    if(!this._responseHeaders)
      return null
    return this.responseHeaders[field] || null

  }

  getAllResponseHeaders():string | null {
    log.verbose('XMLHttpRequest get all headers', this._responseHeaders)
    if(!this._responseHeaders)
      return ''
    let result = ''
    let respHeaders = this.responseHeaders
    for(let i in respHeaders) {
      result += `${i}:${respHeaders[i]}\r\n`
    }
    return result
  }

  _headerReceived(e) {
    log.verbose('header received ', this._task.taskId, e)
    this.responseURL = this._url
    if(e.state === "2") {
      this._responseHeaders = e.headers
      this._statusText = e.status
      this._responseType = e.respType || ''
      this._status = Math.floor(e.status)
      this._dispatchReadStateChange(XMLHttpRequest.HEADERS_RECEIVED)
    }
  }

  _uploadProgressEvent(send:number, total:number) {
    console.log('_upload', this.upload)
    if(!this._uploadStarted) {
      this.upload.dispatchEvent('loadstart')
      this._uploadStarted = true
    }
    if(send >= total)
      this.upload.dispatchEvent('load')
    this.upload.dispatchEvent('progress', new ProgressEvent(true, send, total))
  }

  _progressEvent(send:number, total:number) {
    log.verbose(this.readyState)
    if(this._readyState === XMLHttpRequest.HEADERS_RECEIVED)
      this._dispatchReadStateChange(XMLHttpRequest.LOADING)
    let lengthComputable = false
    if(total && total >= 0)
        lengthComputable = true
    let e = new ProgressEvent(lengthComputable, send, total)
    this.dispatchEvent('progress', e)
  }

  _onError(err) {
    log.verbose('XMLHttpRequest error', err)
    this._statusText = err
    this._status = String(err).match(/\d+/)
    this._status = this._status ? Math.floor(this.status) : 404
    this._dispatchReadStateChange(XMLHttpRequest.DONE)
    if(err && String(err.message).match(/(timed\sout|timedout)/)) {
      this.dispatchEvent('timeout')
    }
    this.dispatchEvent('loadend')
    this.dispatchEvent('error', {
      type : 'error',
      detail : err
    })
    this.clearEventListeners()
  }

  _onDone(resp) {
    log.verbose('XMLHttpRequest done', this._url, resp)
    this._statusText = this._status
    if(resp) {
      switch(resp.type) {
        case 'base64' :
          if(this._responseType === 'json') {
              this._responseText = resp.text()
              this._response = resp.json()
          }
          else {
            this._responseText = resp.text()
            this._response = this.responseText
          }
        break;
        case 'path' :
          this.response = resp.blob()
        break;
        default :
          this._responseText = resp.text()
          this._response = this.responseText
        break;
      }
      this.dispatchEvent('loadend')
      this.dispatchEvent('load')
      this._dispatchReadStateChange(XMLHttpRequest.DONE)
    }
    this.clearEventListeners()
  }

  _dispatchReadStateChange(state) {
    this._readyState = state
    if(typeof this._onreadystatechange === 'function')
      this._onreadystatechange()
  }

  set onreadystatechange(fn:() => void) {
    log.verbose('XMLHttpRequest set onreadystatechange', fn.toString())
    this._onreadystatechange = fn
  }

  get onreadystatechange() {
    return this._onreadystatechange
  }

  get readyState() {
    log.verbose('get readyState', this._readyState)
    return this._readyState
  }

  get status() {
    log.verbose('get status', this._status)
    return this._status
  }

  get statusText() {
    log.verbose('get statusText', this._statusText)
    return this._statusText
  }

  get response() {
    log.verbose('get response', this._response)
    return this._response
  }

  get responseText() {
    log.verbose('get responseText', this._responseText)
    return this._responseText
  }

  get responseURL() {
    log.verbose('get responseURL', this._responseURL)
    return this._responseURL
  }

  get responseHeaders() {
    log.verbose('get responseHeaders', this._responseHeaders)
    return this._responseHeaders
  }

  set timeout(val) {
    this._timeout = val*1000
    log.verbose('set timeout', this._timeout)
  }

  get timeout() {
    log.verbose('get timeout', this._timeout)
    return this._timeout
  }

  get responseType() {
    log.verbose('get response type', this._responseType)
    return this._responseType
  }

}
