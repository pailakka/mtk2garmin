var fs = require('fs');
var DOMParser = require('xmldom').DOMParser;
var XMLSerializer = require('xmldom').XMLSerializer;
var sharp = require('sharp');

var stylef = fs.readFileSync('Peruskartta.xml', {
    encoding: 'utf-8'
});

var doc = new DOMParser().parseFromString(stylef);

var rules = doc.getElementsByTagName('rule');

wanted_tags = {
    'line': true,
    'area': true,
    'symbol': true,
    'lineSymbol': true,
    'caption': true,
    'pathText': true
};

wanted_attr = {
    'font-size': true,
    'k': true,
    'stroke': true,
    'stroke-width': true,
    'font-style': true,
    'fill': true,
    'stroke-linecap': true,
    'stroke-dasharray': true,
    'src': true
};

var svg_to_convert = [];

var handleRuleTag = function(r) {
    for (var j = 0; j < r.childNodes.length; j++) {
        var c = r.childNodes[j];

        if (c.tagName === "rule") {
            return handleRuleTag(c);
        }

        if (c.nodeType != 1) continue;

        if (!wanted_tags[c.tagName]) {
            r.removeChild(c);
            //console.log('tag',c.tagName,'removed from',r.getAttribute('k'),'=',r.getAttribute('v'));
            continue;
        }

        var delattrs = [];
        for (var k = 0; k < c.attributes.length; k++) {
            var a = c.attributes[k];
            if (!wanted_attr[a.name]) {
                delattrs.push(a.name);
            }
        }

        if (c.getAttribute('src')) {
            var src = c.getAttribute('src');

            if (!src.endsWith('.svg')) {
                c.setAttribute('src', src.replace('/mml/', '/mml_v3/'));
                continue;
            }

            var npath = src.replace('/mml/', '/mml_v3/').replace('.svg', '.png');
            var npath2 = src.replace('/mml/', '/mml_symbols/');
            fs.createReadStream(src.substr(6)).pipe(fs.createWriteStream(npath2.substr(6)));


            svg_to_convert.push({
                "input": [src.substr(6), 32],
                "output": [npath.substr(6), 32]
            });


            this.npath = npath;
            sharp(src.substr(6))
                .resize(
                    c.getAttribute('symbol-width') ? parseInt(c.getAttribute('symbol-width')) : 1,
                    c.getAttribute('symbol-height') ? parseInt(c.getAttribute('symbol-height')) : 1
                )
                .toFile(npath.substr(6), function(err) {
                    console.log(this.npath, 'converted');
                }.apply(this));


            c.setAttribute('src', npath);
        }


        if (delattrs.length > 0) {
            delattrs.forEach(function(da, l) {
                c.removeAttribute(da);
            });
            //console.log('attributes',delattrs,'removed from',c.tagName,'in',r.getAttribute('k'),'=',r.getAttribute('v'))
        }



    }
}

for (var i = 0; i < rules.length; i++) {
    var r = rules[i];
    handleRuleTag(r);

}


fs.writeFileSync('Peruskartta_v3.xml', new XMLSerializer().serializeToString(doc));