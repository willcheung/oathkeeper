function parse(listitem) {
//	var nodes = $("tr.acZ");
//	if (nodes.length < 2) return null;
	if (listitem == null) return null;
	
//	var sender = $(nodes[0]).find('.gD');
//	var recipients = $(nodes[1]).find('.g2');
	
	var sender = $(listitem).find('.gD');
	var recipients = $(listitem).find('.g2');

	
	console.log("Sender: " + $(sender).attr('name') + " <" + $(sender).attr('email') + ">");
	
	for (var i = 0; i < recipients.length; ++i) {
		var recipient = recipients[i];
		console.log("Recipient: " + $(recipient).attr('name') + " <" + $(recipient).attr('email') + ">");
	}
//	return nodes;
}

$(document).click(function(event) {
	console.log(event.target);
	var listitem = $(event.target).closest("div[role='listitem']");
	if (listitem != null) {
		parse(listitem);
	}
});