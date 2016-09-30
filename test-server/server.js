/**
 * @author wkh237
 * @description react-native-fetch-blob test & dev server
 */

var express = require('express')
var bodyParser = require('body-parser')
var chokidar = require('chokidar')
var multer = require('multer')
var upload = multer({dest : 'uploads/'})
var chalk = require('chalk')
var mkdirp = require('mkdirp')
var dirname = require('path').dirname
var app = express()
var fs = require('fs')
var https = require('https')


var JS_SOURCE_PATH = '../test/',
    LIB_SOURCE_PATH = '../src/',
    NODE_MODULE_MODULE_PATH = '../RNFetchBlobTest/node_modules/react-native-fetch-blob/',
    APP_SOURCE_PATH = '../RNFetchBlobTest/'

// watch test app source
watch(JS_SOURCE_PATH, APP_SOURCE_PATH)
// watch lib js source
watch(LIB_SOURCE_PATH, NODE_MODULE_MODULE_PATH, {ignored: /\.\.\/src\/(android|ios)\//})

// https
var server = https.createServer({
  key : fs.readFileSync('./key.pem'),
  cert : fs.readFileSync('./cert.pem')
}, app).listen(8124, function(err){
  if(!err)
    console.log('SSL test server running at port ',8124)
})

app.disable('etag')

// http
app.listen(8123, function(err){
  if(!err)
    console.log('test server running at port ',8123)
})


app.use(function(req,res,next){
  console.log(chalk.green('request url=') + chalk.magenta(req.url))
  next()
})

app.use('/upload-form', function(req, res, next) {
  console.log(req.headers)
  // req.on('data', (chunk) => {
    // console.log(String(chunk,'utf8'))
  // })
  // req.on('end', () => {
    next()
  // })
})

var count = 0

app.use(function(req, res, next) {
  console.log(req.url, ++count);
  next();
})

app.get('/video/:count', (req, res) => {
  var count = 0
  res.set('Content-Type', 'multipart/x-mixed-replace; boundary="---osclivepreview---"')
  res.write('---osclivepreview---\r\n')
  var it = setInterval(() => {
    if(count > req.params.count) {
      res.end()
    }
    else {
      count ++
      res.write('Content-Type: image/jpeg\r\n')
      var data = fs.readFileSync('./cat_fu_mp4/img' + pad(count,5) + '.jpeg')
      console.log('frame', count, 'length=', new Buffer(data).length, 'base64=', new Buffer(data).toString('base64').length)
      console.log(data)
      fs.writeFileSync('test.jpeg')
      res.write('Content-Length: '+data.length+'\r\n')
      res.write('\r\n')
      res.write(new Buffer(data), 'binary')
      res.write('\r\n\r\n---osclivepreview---\r\n')
    }
  }, 66)

})

app.all('/upload', (req, res) => {
  console.log(req.headers)
  res.send(req.headers)
})

app.get('/unicode', (req, res) => {
  res.send({ data:'你好!'})
})

app.all('/echo', (req, res) => {
  var body = ''
  req.on('data', (chunk) => {
    body+=chunk
  })
  req.on('end', () => {
    res.send({
      headers :  req.headers,
      body : body
    })
  })
})

app.use(upload.any())
app.use('/public', express.static('./public'))
// for redirect test
app.get('/redirect', function(req, res) {
  res.redirect('/public/github.png')
})

app.all('/params', function(req, res) {
  console.log(req.url)
    var resp =
      {
         time : req.query.time,
         name : req.query.name,
         lang : req.query.lang
      }
    console.log(resp)
    res.send(resp)
})

// return an empty response
app.all('/empty', function(req, res) {
  res.send('')
})

app.delete('/hey', function(req, res) {
  res.send('man')
})

app.get('/stress/:id', function(req, res) {
  res.sendFile(process.cwd() + '/public/github.png')
})

app.post('/mime', mimeCheck)
app.put('/mime', mimeCheck)

function mimeCheck(req, res) {
  console.log(req.files)
  var mimes = []
  for(var i in req.files) {
    mimes.push(req.files[i].mimetype)
  }
  res.send(mimes)
}

// handle multipart/form-data request
app.post('/upload-form', formUpload)

app.put('/upload-form', formUpload)

// for XHR tests
//
app.all('/xhr-code/:code', (req, res) => {
  console.log('code = ', req.params.code)
  res.status(Math.floor(req.params.code)).send()
})

app.all('/content-length', (req, res) => {
  console.log(req.headers)
  res.send(req.headers['Content-Length'])
})

app.all('/xhr-header', (req, res) => {
  console.log(req.headers)
  // res.header('Content-Type', 'application/json')
  res.send(req.headers)
})

app.post('/upload_urlencode', bodyParser.urlencoded({ extended : true }), (req, res) => {
  console.log(JSON.stringify(req.headers))
  console.log(JSON.stringify(req.body))
  res.status(200).send(req.body)
})

app.all('/timeout408/:time', (req, res) => {
  setTimeout(function() {
    res.status(408).send('request timed out.')
  }, 5000)
})

app.get('/10s-download', (req,res) => {
  var count = 0
  var data = ''
  for(var i =0;i<1024000;i++)
    data += '1'
  res.set('Contet-Length', 1024000*10)
  var it = setInterval(() => {
    res.write(data)
    count++
    if(count == 10) {
      clearInterval(it)
      res.end()
    }
  }, 1000)
})

app.all('/long', (req, res) => {
  var count = 0;
  var it = setInterval(() => {
    console.log('write data', count)
    res.write('a')
    if(++count >60){
      clearInterval(it)
      res.end()
    }
  }, 1000);

})

app.all('/timeout', (req, res) => {
})

function formUpload(req, res) {
  console.log(req.headers)
  console.log(req.body)
  console.log(req.files)
  if(Array.isArray(req.files)) {
    req.files.forEach((f) => {
      console.log(process.cwd() + f.path, '=>', process.cwd() + '/public/' + f.originalname)
      fs.renameSync('./' + f.path, './public/'+ f.originalname)
    })
  }
  res.status(200).send({
    fields : req.body,
    files : req.files
  })
}

function watch(source, dest, ignore) {
  // watch files in  test folder
  chokidar
    .watch(source, ignore)
    .on('add', function(path) {
      console.log(chalk.magenta('file created'), path)
      var targetPath = String(path).replace(source, dest)
      mkdirp(dirname(targetPath), function (err) {
      if (err) return cb(err)
        fs.writeFileSync(targetPath, fs.readFileSync(path))
      })
    })
    .on('change', function(path) {
      console.log(chalk.green('file changed'), path)
      var targetPath = String(path).replace(source, dest)
      mkdirp(dirname(targetPath), function (err) {
      if (err) return cb(err)
        fs.writeFileSync(targetPath, fs.readFileSync(path))
      })
    })
    .on('unlink', function(path) {
      console.log(chalk.red('file removed'), path)
      var targetPath = String(path).replace(source, dest)
      mkdirp(dirname(targetPath), function (err) {
      if (err) return cb(err)
        fs.unlinkSync(targetPath)
      })
    })
    .on('error', function(err){
      console.log(err)
    })
}

function pad(n, width, z) {
  z = z || '0';
  n = n + '';
  return n.length >= width ? n : new Array(width - n.length + 1).join(z) + n;
}
