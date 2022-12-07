
let subscriptionMonth = 10.60;
let indexPrices = [0.1423, 0, 0, 0, 0, 0, 0, 0, 0, 0];



let currentData = {};

function setHTML(id, html) {
    document.getElementById(id).innerHTML = html;
}

function leftPadStr(value, pad, count) {
    value = value + '';
    while (value.length < count) {
        value = pad + value;
    }
    return value;
}

function onLiveDataReceived(data) {
    if (data.serialNumber != currentData.serialNumber) {
        setHTML("live-data-serial", data.serialNumber);
    }
    if (data.prm != currentData.prm) {
        setHTML("live-data-prm", data.prm);
    }
    if (data.subscribedOption != currentData.subscribedOption) {
        setHTML("live-data-OPTARIF", data.subscribedOption);
    }
    if (data.refPower != currentData.refPower) {
        setHTML("live-data-pref", data.refPower);
    }
    if (data.cutPower != currentData.cutPower) {
        setHTML("live-data-pcoup", data.cutPower);
    }
    if (data.maxPowerToday != currentData.maxPowerToday) {
        setHTML("live-data-pj", data.maxPowerToday);
        var d = new Date(data.maxPowerTimeToday);
        setHTML("live-data-pj-time", d.toLocaleTimeString());
    }
    if (data.maxPowerYesterday != currentData.maxPowerYesterday) {
        setHTML("live-data-pj-1", data.maxPowerYesterday);
        var d = new Date(data.maxPowerTimeYesterday);
        setHTML("live-data-pj-1-time", d.toLocaleTimeString());
    }
    if (data.appPower != currentData.appPower) {
        setHTML("live-data-papp", data.appPower);
    }
    if (data.rmsVoltage != currentData.rmsVoltage) {
        setHTML("live-data-urms", data.rmsVoltage);
    }

    if (data.indexes != currentData.indexes) {
        var parent = document.getElementById('live-data-indexes');
        var template = document.getElementById('live-data-indexes-template');
        for (var i = 0; i < data.indexes.length; i++) {
            var value = data.indexes[i];
            var name = data.indexNames[i] || ('Index ' + (i + 1));
            if (value <= 0)
                continue;
            var el = document.getElementById('live-data-index-' + i);
            if (el == null) {
                el = template.cloneNode(true);
                el.id = 'live-data-index-' + i;
                el.style.display = "";
                if (data.currIndex == i)
                    el.classList.add('index-current');
                el = parent.appendChild(el);
                var nameEl = el.querySelector('.name');
                nameEl.innerHTML = name;
            }
            var kWhEl = el.querySelector('.int-value');
            var whEl = el.querySelector('.dec-value');
            kWhEl.innerHTML = Math.floor(value / 1000);
            whEl.innerHTML = leftPadStr(value % 1000, '0', 3);
        }
    }

    if (data.currIndex != currentData.currIndex) {
        var oldEl = document.getElementById('live-data-index-' + currentData.currIndex);
        if (oldEl != null) {
            oldEl.classList.remove('index-current');
        }
        var newEl = document.getElementById('live-data-index-' + data.currIndex);
        if (newEl != null) {
            newEl.classList.add('index-current');
        }
    }

    var dayPriceSum = 0.0;

    if (data.indexesMidnight != currentData.indexesMidnight) {
        var parentIndex = document.getElementById('live-data-indexes-day');
        var templateIndex = document.getElementById('live-data-indexes-day-template');
        var parentPrice = document.getElementById('live-data-price-day');
        var templatePrice = document.getElementById('live-data-price-day-template');
        for (var i = 0; i < data.indexesMidnight.length; i++) {
            var value = data.indexes[i] - data.indexesMidnight[i];
            var name = data.indexNames[i] || ('Index ' + (i + 1));
            if (value <= 0)
                continue;
            var el = document.getElementById('live-data-index-day-' + i);
            if (el == null) {
                el = templateIndex.cloneNode(true);
                el.id = 'live-data-index-day-' + i;
                el.style.display = "";
                el = parentIndex.appendChild(el);
                var nameEl = el.querySelector('.name');
                nameEl.innerHTML = name;
            }
            var kWhEl = el.querySelector('.int-value');
            var whEl = el.querySelector('.dec-value');
            kWhEl.innerHTML = Math.floor(value / 1000);
            whEl.innerHTML = leftPadStr(value % 1000, '0', 3);

            el = document.getElementById('live-data-price-day-' + i);
            if (el == null) {
                el = templatePrice.cloneNode(true);
                el.id = 'live-data-price-day-' + i;
                el.style.display = "";
                el = parentPrice.appendChild(el);
                var nameEl = el.querySelector('.name');
                nameEl.innerHTML = name;
            }
            var price = value * (indexPrices[i] / 1000);
            dayPriceSum += price;
            var euroEl = el.querySelector('.int-value');
            var centsEl = el.querySelector('.dec-value');
            var perkWh = el.querySelector('.live-data-price-per-kwh');
            euroEl.innerHTML = Math.floor(price);
            centsEl.innerHTML = leftPadStr(Math.floor(price * 10000) % 10000, '0', 4);
            perkWh.innerHTML = indexPrices[i];
        }
    }

    var dayPrice = subscriptionMonth / data.nbDayThisMonth;
    var currDayPrice = dayPrice * ((data.date - data.getDayStartTime) / 86400000);
    dayPriceSum += currDayPrice;
    setHTML('live-data-price-sub-int', Math.floor(currDayPrice));
    setHTML('live-data-price-sub-dec', leftPadStr(Math.floor(currDayPrice * 10000) % 10000, '0', 4));

    setHTML('live-data-price-total-int', Math.floor(dayPriceSum));
    setHTML('live-data-price-total-dec', leftPadStr(Math.floor(dayPriceSum * 10000) % 10000, '0', 4));

    setHTML('live-data-price-sub-per-month', subscriptionMonth);



    if (data.sensorsData != currentData.sensorsData) {
        var parent = document.getElementById('live-data-sensors');
        var template = document.getElementById('live-data-sensors-template');
        for (var name in data.sensorsData) {
            var temp = data.sensorsData[name].temp;
            var hum = data.sensorsData[name].hum;
            var el = document.getElementById('live-data-sensors-' + name);
            if (el == null) {
                el = template.cloneNode(true);
                el.id = 'live-data-sensors-' + name;
                el.style.display = "";
                el = parent.appendChild(el);
                var nameEl = el.querySelector('.name');
                nameEl.innerHTML = name;
            }
            var intEl = el.querySelector('.int-value');
            var decEl = el.querySelector('.dec-value');
            var humEl = el.querySelector('.live-data-sensor-hum');
            intEl.innerHTML = Math.floor(temp);
            decEl.innerHTML = leftPadStr(Math.floor(temp * 10) % 10, '0', 1);
            humEl.innerHTML = Math.floor(hum);
        }
    }

    currentData = data;
}

function loginSuccess() {
    document.getElementById("logged-in-content").style.display = "";
    document.getElementById("login-form").style.display = "none";
    setHTML("live-login-status", "");
}

function loginFail(message) {
    document.getElementById("logged-in-content").style.display = "none";
    document.getElementById("login-form").style.display = "";
    setHTML("live-login-status", message);
}

function updateLiveData(afterLogin = false) {
    var xhr = new XMLHttpRequest();
    xhr.onload = function() {
        if (xhr.readyState == 4) {
            if (xhr.status == 403) {
                loginFail(afterLogin ? "Mauvais identifiants" : "");
                return;
            }
            loginSuccess();
            if (xhr.status == 200) {
                setHTML("live-data-status", "");
                var jsonObj = JSON.parse(xhr.responseText);
                onLiveDataReceived(jsonObj.data);
                var delay = jsonObj.avgUpdateInterval - (jsonObj.now - jsonObj.lastUpdate) + 100;
                if (delay < 200) // dont spam if data source is too late than usual
                    delay = 200;
                setTimeout(function() { updateLiveData(false); }, delay);
            }
            else {
                setHTML("live-data-status", "Erreur de connexion (backend offline)");
                setTimeout(function() { updateLiveData(false); }, 5000);
            }
        }
    }
    xhr.ontimeout = function() {
        setHTML("live-data-status", "Erreur de connexion (timeout)");
        setTimeout(function() { updateLiveData(false); }, 5000);
    }
    xhr.onerror = function() {
        setHTML("live-data-status", "Erreur de connexion");
        setTimeout(function() { updateLiveData(false); }, 5000);
    }
    xhr.timeout = 5000;
    xhr.open("GET", "/rest/currentData", true);
    xhr.send();
}



document.getElementById("login_frame").onload = function() {
    updateLiveData(true);
}


updateLiveData(false);
