Delivered-To: indifferenzetester@gmail.com
Received: by 10.114.199.228 with SMTP id jn4csp1382006ldc;
        Sun, 3 May 2015 22:50:34 -0700 (PDT)
X-Received: by 10.152.121.42 with SMTP id lh10mr18619560lab.0.1430718196960;
        Sun, 03 May 2015 22:43:16 -0700 (PDT)
Authentication-Results: mx.google.com;
       spf=softfail (google.com: best guess record for domain of transitioning 3XmLKVAYOB_ckakZbjbnloqdgdmc.bnlvbgdtmfbnloqdgdmc.bnl@calendar-server.bounces.google.com does not designate <unknown> as permitted sender) smtp.mail=3XmLKVAYOB_ckakZbjbnloqdgdmc.bnlvbgdtmfbnloqdgdmc.bnl@calendar-server.bounces.google.com;
       dkim=pass header.i=@google.com
Received-SPF: softfail (google.com: best guess record for domain of transitioning 3XmLKVAYOB_ckakZbjbnloqdgdmc.bnlvbgdtmfbnloqdgdmc.bnl@calendar-server.bounces.google.com does not designate <unknown> as permitted sender)
Received: by 10.152.125.41 with POP3 id mn9mf4799609lab.18;
        Sun, 03 May 2015 22:43:16 -0700 (PDT)
X-Gmail-Fetch-Info: wcheung@comprehend.com 1 pop.gmail.com 995 wcheung@comprehend.com
Delivered-To: wcheung@comprehend.com
Received: by 10.112.142.72 with SMTP id ru8csp3510lbb;
        Thu, 29 Jan 2015 08:40:00 -0800 (PST)
X-Received: by 10.68.131.194 with SMTP id oo2mr1955394pbb.29.1422549599221;
        Thu, 29 Jan 2015 08:39:59 -0800 (PST)
Return-Path: <3XmLKVAYOB_ckakZbjbnloqdgdmc.bnlvbgdtmfbnloqdgdmc.bnl@calendar-server.bounces.google.com>
Received: from mail-pa0-x249.google.com (mail-pa0-x249.google.com. [2607:f8b0:400e:c03::249])
        by mx.google.com with ESMTPS id qs6si10491896pbc.100.2015.01.29.08.39.58
        for <wcheung@comprehend.com>
        (version=TLSv1 cipher=ECDHE-RSA-RC4-SHA bits=128/128);
        Thu, 29 Jan 2015 08:39:59 -0800 (PST)
Received-SPF: pass (google.com: domain of 3XmLKVAYOB_ckakZbjbnloqdgdmc.bnlvbgdtmfbnloqdgdmc.bnl@calendar-server.bounces.google.com designates 2607:f8b0:400e:c03::249 as permitted sender) client-ip=2607:f8b0:400e:c03::249;
Authentication-Results: mx.google.com;
       spf=pass (google.com: domain of 3XmLKVAYOB_ckakZbjbnloqdgdmc.bnlvbgdtmfbnloqdgdmc.bnl@calendar-server.bounces.google.com designates 2607:f8b0:400e:c03::249 as permitted sender) smtp.mail=3XmLKVAYOB_ckakZbjbnloqdgdmc.bnlvbgdtmfbnloqdgdmc.bnl@calendar-server.bounces.google.com;
       dkim=pass header.i=@comprehend.com
Received: by mail-pa0-f73.google.com with SMTP id lj1so6357052pab.0
        for <wcheung@comprehend.com>; Thu, 29 Jan 2015 08:39:58 -0800 (PST)
DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/relaxed;
        d=google.com; s=20120113;
        h=mime-version:reply-to:sender:auto-submitted:message-id:date:subject
         :from:to:content-type;
        bh=krVpiZ4qFshiAejUOJUs4KaMKpj09uoKyr/3K3eL9q8=;
        b=pL81/F8VtHP92exlWU61Mv0QURFKJZa5H9uf1/BduVMAFbnT5zMIWqxgQhighMdj/n
         UjRkQw7ZR8+D9Zkd/K16EuWExjekqiZrsJr8AQATmhrgQOxc8ADhk/Lu3R8Ga/gFttTR
         yNpPZtKBXoJqeY30lYXhEMmhcjDCHjS8m4y9NTSVDBEomZ5xf6q2bMZwDppcwGIMRPAr
         MenUQLPGRSS1zCRAGgIdLTNr5kV6Ibj/vMgWiq3/4jZzNv25Ux+a62o85nFKwpx1te2o
         DRJpb7/O2IY8gkCX3YzZOnJcuMDHiqDWLy+Z3Ya89cRjc6oYKZ+uTvxZ+T64/lFzEAzE
         8BAQ==
DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/relaxed;
        d=comprehend.com; s=google;
        h=mime-version:reply-to:sender:auto-submitted:message-id:date:subject
         :from:to:content-type;
        bh=krVpiZ4qFshiAejUOJUs4KaMKpj09uoKyr/3K3eL9q8=;
        b=UgyCiqRWmWZNZqXAyLjkuSQwy87dFwCOAtebdP1kMxub/CHNMSiSA3iRu3elWtWd0v
         fbqhF4/Y7/mxvHJtCDqUDYMdZiMLomUTn9HvUJofG44Eo9ksrRIdy6zrqQppdzIs+15u
         oqa1YUluHrLIN676chMaHYfbgGV0y+ffhPsmE=
X-Google-DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/relaxed;
        d=1e100.net; s=20130820;
        h=x-gm-message-state:mime-version:reply-to:sender:auto-submitted
         :message-id:date:subject:from:to:content-type;
        bh=krVpiZ4qFshiAejUOJUs4KaMKpj09uoKyr/3K3eL9q8=;
        b=L0j1sN/G2C/x9FYYpXg/YjrN5aEGmSMEI6UgETq9epNc65sbuG2pS7FXQs111u1bgU
         terhR82kRjjUaA0GPLHs7ime8zrN9JIYsROidMF9sMl1neMquzWvXFHYSHrHWUu2765m
         SnUgDcmW02LA/Ys6bSOW8+UXOJ1b6ULUplmHpVRjcU6AaX8junYQJwztKreLaC/Ghfu9
         eUABDm4R3TMsQ1VXTcijgHdmD8tpE/KDu5TB0XcQzV1TqSdPCPW1UNPK4EYLmAyZArxD
         +/7r+8XdUtKovYATcoUtBb0Xis9ekReD4AsYe2Tsx8WLVaJWPhPn6dG2eDfFdkktuU5D
         2HhQ==
X-Gm-Message-State: ALoCoQlBL6xioHlInqkmfL43pYVN1fKyzD810cmziFM4mXKqRgPqni+nn65h1HufKJmsUwCsjTAp
MIME-Version: 1.0
X-Received: by 10.66.124.198 with SMTP id mk6mr1061780pab.22.1422549598287;
 Thu, 29 Jan 2015 08:39:58 -0800 (PST)
Reply-To: Lee Black <lblack@comprehend.com>
Sender: Google Calendar <calendar-notification@google.com>
Auto-Submitted: auto-generated
Message-ID: <001a11343d847b5c1d050dcd2324@google.com>
Date: Thu, 29 Jan 2015 16:39:58 +0000
Subject: Invitation: Prep for AZ call on 2/4 - game plan @ Tue Feb 3, 2015
 9:30am - 10:30am (Will Cheung)
From: Lee Black <lblack@comprehend.com>
To: "wcheung@comprehend.com" <wcheung@comprehend.com>, Mike Connolly <mconnolly@comprehend.com>, 
	Rick Morrison <rmorrison@comprehend.com>, Jud Gardner <jgardner@comprehend.com>, 
	Arjun Devgan <adevgan@comprehend.com>
Content-Type: multipart/mixed; boundary=001a11343d847b5bf6050dcd2323
Gmail-Message-Id: 14b36903e09359a6
Gmail-Thread-Id: 14b36903e09359a6
X-Source: gmail
X-Private-Id: 14b36903e09359a6

--001a11343d847b5bf6050dcd2323
Content-Type: multipart/alternative; boundary=001a11343d847b5bf0050dcd2321

--001a11343d847b5bf0050dcd2321
Content-Type: text/plain; charset=ISO-8859-1; format=flowed; delsp=yes

You have been invited to the following event.

Title: Prep for AZ call on 2/4 - game plan
Dial-In Number:
(760) 569-7171

Participants:
721407088


Team, lets get together and discuss what we anticipate AZ asking for and  
how we might like to support it.


When: Tue Feb 3, 2015 9:30am - 10:30am Pacific Time
Where: Pasteur
Calendar: Will Cheung
Who:
     * Lee Black - organizer
     * Mike Connolly
     * Rick Morrison
     * Will Cheung
     * Jud Gardner
     * Arjun Devgan

Event details:  
https://www.google.com/calendar/event?action=VIEW&eid=bmlyZjdxdWcxNTc4ODBxbTBzMWpxYWxjNm8gd2NoZXVuZ0Bjb21wcmVoZW5kLmNvbQ&tok=MjEjbGJsYWNrQGNvbXByZWhlbmQuY29tMjg2NTE2ZmIzNzQ0ODJjYmQ3MTc3Mjc0M2ZlNjczM2Q4ZDIxYTY4MA&ctz=America/Los_Angeles&hl=en

Invitation from Google Calendar: https://www.google.com/calendar/

You are receiving this email at the account wcheung@comprehend.com because  
you are subscribed for invitations on calendar Will Cheung.

To stop receiving these emails, please log in to  
https://www.google.com/calendar/ and change your notification settings for  
this calendar.

--001a11343d847b5bf0050dcd2321
Content-Type: text/html; charset=ISO-8859-1
Content-Transfer-Encoding: quoted-printable

<div style=3D""><table cellspacing=3D"0" cellpadding=3D"8" border=3D"0" sum=
mary=3D"" style=3D"width:100%;font-family:Arial,Sans-serif;border:1px Solid=
 #ccc;border-width:1px 2px 2px 1px;background-color:#fff;" itemscope itemty=
pe=3D"http://schema.org/Event"><tr><td><meta itemprop=3D"eventStatus" conte=
nt=3D"http://schema.org/EventScheduled"/><div style=3D"padding:2px"><span i=
temprop=3D"publisher" itemscope itemtype=3D"http://schema.org/Organization"=
><meta itemprop=3D"name" content=3D"Google Calendar"/></span><meta itemprop=
=3D"eventId/googleCalendar" content=3D"nirf7qug157880qm0s1jqalc6o"/><div st=
yle=3D"float:right;font-weight:bold;font-size:13px"> <a href=3D"https://www=
.google.com/calendar/event?action=3DVIEW&amp;eid=3DbmlyZjdxdWcxNTc4ODBxbTBz=
MWpxYWxjNm8gd2NoZXVuZ0Bjb21wcmVoZW5kLmNvbQ&amp;tok=3DMjEjbGJsYWNrQGNvbXByZW=
hlbmQuY29tMjg2NTE2ZmIzNzQ0ODJjYmQ3MTc3Mjc0M2ZlNjczM2Q4ZDIxYTY4MA&amp;ctz=3D=
America/Los_Angeles&amp;hl=3Den" style=3D"color:#20c;white-space:nowrap" it=
emprop=3D"url">more details &raquo;</a><br></div><h3 style=3D"padding:0 0 6=
px 0;margin:0;font-family:Arial,Sans-serif;font-size:16px;font-weight:bold;=
color:#222"><span itemprop=3D"name">Prep for AZ call on 2/4 - game plan</sp=
an></h3><div style=3D"padding-bottom:15px;font-size:13px;color:#222;white-s=
pace:pre-wrap!important;white-space:-moz-pre-wrap!important;white-space:-pr=
e-wrap!important;white-space:-o-pre-wrap!important;white-space:pre;word-wra=
p:break-word">Dial-In Number: <br />(760) 569-7171<br /> <br />Participants=
: <br />721407088<p><br />Team, lets get together and discuss what we antic=
ipate AZ asking for and how we might like to support it.  <p></p></p></div>=
<table cellpadding=3D"0" cellspacing=3D"0" border=3D"0" summary=3D"Event de=
tails"><tr><td style=3D"padding:0 1em 10px 0;font-family:Arial,Sans-serif;f=
ont-size:13px;color:#888;white-space:nowrap" valign=3D"top"><div><i style=
=3D"font-style:normal">When</i></div></td><td style=3D"padding-bottom:10px;=
font-family:Arial,Sans-serif;font-size:13px;color:#222" valign=3D"top"><tim=
e itemprop=3D"startDate" datetime=3D"20150203T173000Z"></time><time itempro=
p=3D"endDate" datetime=3D"20150203T183000Z"></time>Tue Feb 3, 2015 9:30am &=
ndash; 10:30am <span style=3D"color:#888">Pacific Time</span></td></tr><tr>=
<td style=3D"padding:0 1em 10px 0;font-family:Arial,Sans-serif;font-size:13=
px;color:#888;white-space:nowrap" valign=3D"top"><div><i style=3D"font-styl=
e:normal">Where</i></div></td><td style=3D"padding-bottom:10px;font-family:=
Arial,Sans-serif;font-size:13px;color:#222" valign=3D"top"><span itemprop=
=3D"location" itemscope itemtype=3D"http://schema.org/Place"><span itemprop=
=3D"name">Pasteur</span><span dir=3D"ltr"> (<a href=3D"https://maps.google.=
com/maps?q=3DPasteur&amp;hl=3Den" style=3D"color:#20c;white-space:nowrap" t=
arget=3D"_blank" itemprop=3D"map">map</a>)</span></span></td></tr><tr><td s=
tyle=3D"padding:0 1em 10px 0;font-family:Arial,Sans-serif;font-size:13px;co=
lor:#888;white-space:nowrap" valign=3D"top"><div><i style=3D"font-style:nor=
mal">Calendar</i></div></td><td style=3D"padding-bottom:10px;font-family:Ar=
ial,Sans-serif;font-size:13px;color:#222" valign=3D"top">Will Cheung</td></=
tr><tr><td style=3D"padding:0 1em 10px 0;font-family:Arial,Sans-serif;font-=
size:13px;color:#888;white-space:nowrap" valign=3D"top"><div><i style=3D"fo=
nt-style:normal">Who</i></div></td><td style=3D"padding-bottom:10px;font-fa=
mily:Arial,Sans-serif;font-size:13px;color:#222" valign=3D"top"><table cell=
spacing=3D"0" cellpadding=3D"0"><tr><td style=3D"padding-right:10px;font-fa=
mily:Arial,Sans-serif;font-size:13px;color:#222"><span style=3D"font-family=
:Courier New,monospace">&#x2022;</span></td><td style=3D"padding-right:10px=
;font-family:Arial,Sans-serif;font-size:13px;color:#222"><div><div style=3D=
"margin:0 0 0.3em 0"><span itemprop=3D"attendee" itemscope itemtype=3D"http=
://schema.org/Person"><span itemprop=3D"name">Lee Black</span><meta itempro=
p=3D"email" content=3D"lblack@comprehend.com"/></span><span style=3D"font-s=
ize:11px;color:#888"> - organizer</span></div></div></td></tr><tr><td style=
=3D"padding-right:10px;font-family:Arial,Sans-serif;font-size:13px;color:#2=
22"><span style=3D"font-family:Courier New,monospace">&#x2022;</span></td><=
td style=3D"padding-right:10px;font-family:Arial,Sans-serif;font-size:13px;=
color:#222"><div><div style=3D"margin:0 0 0.3em 0"><span itemprop=3D"attend=
ee" itemscope itemtype=3D"http://schema.org/Person"><span itemprop=3D"name"=
>Mike Connolly</span><meta itemprop=3D"email" content=3D"mconnolly@comprehe=
nd.com"/></span></div></div></td></tr><tr><td style=3D"padding-right:10px;f=
ont-family:Arial,Sans-serif;font-size:13px;color:#222"><span style=3D"font-=
family:Courier New,monospace">&#x2022;</span></td><td style=3D"padding-righ=
t:10px;font-family:Arial,Sans-serif;font-size:13px;color:#222"><div><div st=
yle=3D"margin:0 0 0.3em 0"><span itemprop=3D"attendee" itemscope itemtype=
=3D"http://schema.org/Person"><span itemprop=3D"name">Rick Morrison</span><=
meta itemprop=3D"email" content=3D"rmorrison@comprehend.com"/></span></div>=
</div></td></tr><tr><td style=3D"padding-right:10px;font-family:Arial,Sans-=
serif;font-size:13px;color:#222"><span style=3D"font-family:Courier New,mon=
ospace">&#x2022;</span></td><td style=3D"padding-right:10px;font-family:Ari=
al,Sans-serif;font-size:13px;color:#222"><div><div style=3D"margin:0 0 0.3e=
m 0"><span itemprop=3D"attendee" itemscope itemtype=3D"http://schema.org/Pe=
rson"><span itemprop=3D"name">Will Cheung</span><meta itemprop=3D"email" co=
ntent=3D"wcheung@comprehend.com"/></span></div></div></td></tr><tr><td styl=
e=3D"padding-right:10px;font-family:Arial,Sans-serif;font-size:13px;color:#=
222"><span style=3D"font-family:Courier New,monospace">&#x2022;</span></td>=
<td style=3D"padding-right:10px;font-family:Arial,Sans-serif;font-size:13px=
;color:#222"><div><div style=3D"margin:0 0 0.3em 0"><span itemprop=3D"atten=
dee" itemscope itemtype=3D"http://schema.org/Person"><span itemprop=3D"name=
">Jud Gardner</span><meta itemprop=3D"email" content=3D"jgardner@comprehend=
.com"/></span></div></div></td></tr><tr><td style=3D"padding-right:10px;fon=
t-family:Arial,Sans-serif;font-size:13px;color:#222"><span style=3D"font-fa=
mily:Courier New,monospace">&#x2022;</span></td><td style=3D"padding-right:=
10px;font-family:Arial,Sans-serif;font-size:13px;color:#222"><div><div styl=
e=3D"margin:0 0 0.3em 0"><span itemprop=3D"attendee" itemscope itemtype=3D"=
http://schema.org/Person"><span itemprop=3D"name">Arjun Devgan</span><meta =
itemprop=3D"email" content=3D"adevgan@comprehend.com"/></span></div></div><=
/td></tr></table></td></tr></table></div><p style=3D"color:#222;font-size:1=
3px;margin:0"><span style=3D"color:#888">Going?&nbsp;&nbsp;&nbsp;</span><wb=
r><strong><span itemprop=3D"action" itemscope itemtype=3D"http://schema.org=
/RsvpAction"><meta itemprop=3D"attendance" content=3D"http://schema.org/Rsv=
pAttendance/Yes"/><span itemprop=3D"handler" itemscope itemtype=3D"http://s=
chema.org/HttpActionHandler"><link itemprop=3D"method" href=3D"http://schem=
a.org/HttpRequestMethod/GET"/><a href=3D"https://www.google.com/calendar/ev=
ent?action=3DRESPOND&amp;eid=3DbmlyZjdxdWcxNTc4ODBxbTBzMWpxYWxjNm8gd2NoZXVu=
Z0Bjb21wcmVoZW5kLmNvbQ&amp;rst=3D1&amp;tok=3DMjEjbGJsYWNrQGNvbXByZWhlbmQuY2=
9tMjg2NTE2ZmIzNzQ0ODJjYmQ3MTc3Mjc0M2ZlNjczM2Q4ZDIxYTY4MA&amp;ctz=3DAmerica/=
Los_Angeles&amp;hl=3Den" style=3D"color:#20c;white-space:nowrap" itemprop=
=3D"url">Yes</a></span></span><span style=3D"margin:0 0.4em;font-weight:nor=
mal"> - </span><span itemprop=3D"action" itemscope itemtype=3D"http://schem=
a.org/RsvpAction"><meta itemprop=3D"attendance" content=3D"http://schema.or=
g/RsvpAttendance/Maybe"/><span itemprop=3D"handler" itemscope itemtype=3D"h=
ttp://schema.org/HttpActionHandler"><link itemprop=3D"method" href=3D"http:=
//schema.org/HttpRequestMethod/GET"/><a href=3D"https://www.google.com/cale=
ndar/event?action=3DRESPOND&amp;eid=3DbmlyZjdxdWcxNTc4ODBxbTBzMWpxYWxjNm8gd=
2NoZXVuZ0Bjb21wcmVoZW5kLmNvbQ&amp;rst=3D3&amp;tok=3DMjEjbGJsYWNrQGNvbXByZWh=
lbmQuY29tMjg2NTE2ZmIzNzQ0ODJjYmQ3MTc3Mjc0M2ZlNjczM2Q4ZDIxYTY4MA&amp;ctz=3DA=
merica/Los_Angeles&amp;hl=3Den" style=3D"color:#20c;white-space:nowrap" ite=
mprop=3D"url">Maybe</a></span></span><span style=3D"margin:0 0.4em;font-wei=
ght:normal"> - </span><span itemprop=3D"action" itemscope itemtype=3D"http:=
//schema.org/RsvpAction"><meta itemprop=3D"attendance" content=3D"http://sc=
hema.org/RsvpAttendance/No"/><span itemprop=3D"handler" itemscope itemtype=
=3D"http://schema.org/HttpActionHandler"><link itemprop=3D"method" href=3D"=
http://schema.org/HttpRequestMethod/GET"/><a href=3D"https://www.google.com=
/calendar/event?action=3DRESPOND&amp;eid=3DbmlyZjdxdWcxNTc4ODBxbTBzMWpxYWxj=
Nm8gd2NoZXVuZ0Bjb21wcmVoZW5kLmNvbQ&amp;rst=3D2&amp;tok=3DMjEjbGJsYWNrQGNvbX=
ByZWhlbmQuY29tMjg2NTE2ZmIzNzQ0ODJjYmQ3MTc3Mjc0M2ZlNjczM2Q4ZDIxYTY4MA&amp;ct=
z=3DAmerica/Los_Angeles&amp;hl=3Den" style=3D"color:#20c;white-space:nowrap=
" itemprop=3D"url">No</a></span></span></strong>&nbsp;&nbsp;&nbsp;&nbsp;<wb=
r><a href=3D"https://www.google.com/calendar/event?action=3DVIEW&amp;eid=3D=
bmlyZjdxdWcxNTc4ODBxbTBzMWpxYWxjNm8gd2NoZXVuZ0Bjb21wcmVoZW5kLmNvbQ&amp;tok=
=3DMjEjbGJsYWNrQGNvbXByZWhlbmQuY29tMjg2NTE2ZmIzNzQ0ODJjYmQ3MTc3Mjc0M2ZlNjcz=
M2Q4ZDIxYTY4MA&amp;ctz=3DAmerica/Los_Angeles&amp;hl=3Den" style=3D"color:#2=
0c;white-space:nowrap" itemprop=3D"url">more options &raquo;</a></p></td></=
tr><tr><td style=3D"background-color:#f6f6f6;color:#888;border-top:1px Soli=
d #ccc;font-family:Arial,Sans-serif;font-size:11px"><p>Invitation from <a h=
ref=3D"https://www.google.com/calendar/" target=3D"_blank" style=3D"">Googl=
e Calendar</a></p><p>You are receiving this email at the account wcheung@co=
mprehend.com because you are subscribed for invitations on calendar Will Ch=
eung.</p><p>To stop receiving these emails, please log in to https://www.go=
ogle.com/calendar/ and change your notification settings for this calendar.=
</p></td></tr></table></div>
--001a11343d847b5bf0050dcd2321
Content-Type: text/calendar; charset=UTF-8; method=REQUEST
Content-Transfer-Encoding: 7bit

BEGIN:VCALENDAR
PRODID:-//Google Inc//Google Calendar 70.9054//EN
VERSION:2.0
CALSCALE:GREGORIAN
METHOD:REQUEST
BEGIN:VEVENT
DTSTART:20150203T173000Z
DTEND:20150203T183000Z
DTSTAMP:20150129T163958Z
ORGANIZER;CN=Lee Black:mailto:lblack@comprehend.com
UID:nirf7qug157880qm0s1jqalc6o@google.com
ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
 TRUE;CN=Mike Connolly;X-NUM-GUESTS=0:mailto:mconnolly@comprehend.com
ATTENDEE;CUTYPE=RESOURCE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TR
 UE;CN=Pasteur;X-NUM-GUESTS=0:mailto:comprehend.com_39313934343435352d393334
 @resource.calendar.google.com
ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
 TRUE;CN=Rick Morrison;X-NUM-GUESTS=0:mailto:rmorrison@comprehend.com
ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED;RSVP=TRUE
 ;CN=Lee Black;X-NUM-GUESTS=0:mailto:lblack@comprehend.com
ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
 TRUE;CN=Will Cheung;X-NUM-GUESTS=0:mailto:wcheung@comprehend.com
ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
 TRUE;CN=Jud Gardner;X-NUM-GUESTS=0:mailto:jgardner@comprehend.com
ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
 TRUE;CN=Arjun Devgan;X-NUM-GUESTS=0:mailto:adevgan@comprehend.com
CREATED:20150129T163957Z
DESCRIPTION:Dial-In Number: \n(760) 569-7171\n \nParticipants: \n721407088\
 n\n\nTeam\, lets get together and discuss what we anticipate AZ asking for 
 and how we might like to support it.  \n\n\nView your event at https://www.
 google.com/calendar/event?action=VIEW&eid=bmlyZjdxdWcxNTc4ODBxbTBzMWpxYWxjN
 m8gd2NoZXVuZ0Bjb21wcmVoZW5kLmNvbQ&tok=MjEjbGJsYWNrQGNvbXByZWhlbmQuY29tMjg2N
 TE2ZmIzNzQ0ODJjYmQ3MTc3Mjc0M2ZlNjczM2Q4ZDIxYTY4MA&ctz=America/Los_Angeles&h
 l=en.
LAST-MODIFIED:20150129T163958Z
LOCATION:Pasteur
SEQUENCE:0
STATUS:CONFIRMED
SUMMARY:Prep for AZ call on 2/4 - game plan
TRANSP:OPAQUE
END:VEVENT
END:VCALENDAR

--001a11343d847b5bf0050dcd2321--
--001a11343d847b5bf6050dcd2323
Content-Type: application/ics; name="invite.ics"
Content-Disposition: attachment; filename="invite.ics"
Content-Transfer-Encoding: base64

QkVHSU46VkNBTEVOREFSDQpQUk9ESUQ6LS8vR29vZ2xlIEluYy8vR29vZ2xlIENhbGVuZGFyIDcw
LjkwNTQvL0VODQpWRVJTSU9OOjIuMA0KQ0FMU0NBTEU6R1JFR09SSUFODQpNRVRIT0Q6UkVRVUVT
VA0KQkVHSU46VkVWRU5UDQpEVFNUQVJUOjIwMTUwMjAzVDE3MzAwMFoNCkRURU5EOjIwMTUwMjAz
VDE4MzAwMFoNCkRUU1RBTVA6MjAxNTAxMjlUMTYzOTU4Wg0KT1JHQU5JWkVSO0NOPUxlZSBCbGFj
azptYWlsdG86bGJsYWNrQGNvbXByZWhlbmQuY29tDQpVSUQ6bmlyZjdxdWcxNTc4ODBxbTBzMWpx
YWxjNm9AZ29vZ2xlLmNvbQ0KQVRURU5ERUU7Q1VUWVBFPUlORElWSURVQUw7Uk9MRT1SRVEtUEFS
VElDSVBBTlQ7UEFSVFNUQVQ9TkVFRFMtQUNUSU9OO1JTVlA9DQogVFJVRTtDTj1NaWtlIENvbm5v
bGx5O1gtTlVNLUdVRVNUUz0wOm1haWx0bzptY29ubm9sbHlAY29tcHJlaGVuZC5jb20NCkFUVEVO
REVFO0NVVFlQRT1SRVNPVVJDRTtST0xFPVJFUS1QQVJUSUNJUEFOVDtQQVJUU1RBVD1ORUVEUy1B
Q1RJT047UlNWUD1UUg0KIFVFO0NOPVBhc3RldXI7WC1OVU0tR1VFU1RTPTA6bWFpbHRvOmNvbXBy
ZWhlbmQuY29tXzM5MzEzOTM0MzQzNDM1MzUyZDM5MzMzNA0KIEByZXNvdXJjZS5jYWxlbmRhci5n
b29nbGUuY29tDQpBVFRFTkRFRTtDVVRZUEU9SU5ESVZJRFVBTDtST0xFPVJFUS1QQVJUSUNJUEFO
VDtQQVJUU1RBVD1ORUVEUy1BQ1RJT047UlNWUD0NCiBUUlVFO0NOPVJpY2sgTW9ycmlzb247WC1O
VU0tR1VFU1RTPTA6bWFpbHRvOnJtb3JyaXNvbkBjb21wcmVoZW5kLmNvbQ0KQVRURU5ERUU7Q1VU
WVBFPUlORElWSURVQUw7Uk9MRT1SRVEtUEFSVElDSVBBTlQ7UEFSVFNUQVQ9QUNDRVBURUQ7UlNW
UD1UUlVFDQogO0NOPUxlZSBCbGFjaztYLU5VTS1HVUVTVFM9MDptYWlsdG86bGJsYWNrQGNvbXBy
ZWhlbmQuY29tDQpBVFRFTkRFRTtDVVRZUEU9SU5ESVZJRFVBTDtST0xFPVJFUS1QQVJUSUNJUEFO
VDtQQVJUU1RBVD1ORUVEUy1BQ1RJT047UlNWUD0NCiBUUlVFO0NOPVdpbGwgQ2hldW5nO1gtTlVN
LUdVRVNUUz0wOm1haWx0bzp3Y2hldW5nQGNvbXByZWhlbmQuY29tDQpBVFRFTkRFRTtDVVRZUEU9
SU5ESVZJRFVBTDtST0xFPVJFUS1QQVJUSUNJUEFOVDtQQVJUU1RBVD1ORUVEUy1BQ1RJT047UlNW
UD0NCiBUUlVFO0NOPUp1ZCBHYXJkbmVyO1gtTlVNLUdVRVNUUz0wOm1haWx0bzpqZ2FyZG5lckBj
b21wcmVoZW5kLmNvbQ0KQVRURU5ERUU7Q1VUWVBFPUlORElWSURVQUw7Uk9MRT1SRVEtUEFSVElD
SVBBTlQ7UEFSVFNUQVQ9TkVFRFMtQUNUSU9OO1JTVlA9DQogVFJVRTtDTj1Bcmp1biBEZXZnYW47
WC1OVU0tR1VFU1RTPTA6bWFpbHRvOmFkZXZnYW5AY29tcHJlaGVuZC5jb20NCkNSRUFURUQ6MjAx
NTAxMjlUMTYzOTU3Wg0KREVTQ1JJUFRJT046RGlhbC1JbiBOdW1iZXI6IFxuKDc2MCkgNTY5LTcx
NzFcbiBcblBhcnRpY2lwYW50czogXG43MjE0MDcwODhcDQogblxuXG5UZWFtXCwgbGV0cyBnZXQg
dG9nZXRoZXIgYW5kIGRpc2N1c3Mgd2hhdCB3ZSBhbnRpY2lwYXRlIEFaIGFza2luZyBmb3IgDQog
YW5kIGhvdyB3ZSBtaWdodCBsaWtlIHRvIHN1cHBvcnQgaXQuICBcblxuXG5WaWV3IHlvdXIgZXZl
bnQgYXQgaHR0cHM6Ly93d3cuDQogZ29vZ2xlLmNvbS9jYWxlbmRhci9ldmVudD9hY3Rpb249VklF
VyZlaWQ9Ym1seVpqZHhkV2N4TlRjNE9EQnhiVEJ6TVdweFlXeGpODQogbThnZDJOb1pYVnVaMEJq
YjIxd2NtVm9aVzVrTG1OdmJRJnRvaz1NakVqYkdKc1lXTnJRR052YlhCeVpXaGxibVF1WTI5dE1q
ZzJODQogVEUyWm1Jek56UTBPREpqWW1RM01UYzNNamMwTTJabE5qY3pNMlE0WkRJeFlUWTRNQSZj
dHo9QW1lcmljYS9Mb3NfQW5nZWxlcyZoDQogbD1lbi4NCkxBU1QtTU9ESUZJRUQ6MjAxNTAxMjlU
MTYzOTU4Wg0KTE9DQVRJT046UGFzdGV1cg0KU0VRVUVOQ0U6MA0KU1RBVFVTOkNPTkZJUk1FRA0K
U1VNTUFSWTpQcmVwIGZvciBBWiBjYWxsIG9uIDIvNCAtIGdhbWUgcGxhbg0KVFJBTlNQOk9QQVFV
RQ0KRU5EOlZFVkVOVA0KRU5EOlZDQUxFTkRBUg0K
--001a11343d847b5bf6050dcd2323--
