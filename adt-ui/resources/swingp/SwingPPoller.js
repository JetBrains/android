class SwingPPoller {
    constructor(showDebugOutput) {
        this.inputHandlers = [];
        this.showDebugOutput = showDebugOutput;
        this.textDecoder = new TextDecoder("utf-8");
        this.poll();
    }

    addInputHandler(handler) {
        this.inputHandlers.push(handler);
    }

    log(line) {
        if (this.showDebugOutput) {
            console.log(line);
        }
    }

    // Long-poll the server in Studio.
    poll() {
        var xhr = new XMLHttpRequest();
        xhr.open('GET', 'http://localhost:61642', true);
        xhr.responseType = "arraybuffer";
        xhr.seenBytes = 0;
        xhr.onload = function() {
            this.endPoll = window.performance.now();

            var newData = xhr.response;
            var offset = 0;
            while (offset < newData.byteLength) {
                // The response is a simple encoding of [(int4 size, byte[size])+].
                // I.e. repeats of four-byte int "size" followed by a byte buffer of "size" length.
                var sizeArray = new Uint8Array(newData, offset, 4);
                var size = (sizeArray[0] | sizeArray[1] << 8 | sizeArray[2] << 16 | sizeArray[3] << 24) >>> 0;
                offset += 4;
                var jsonChunk = this.textDecoder.decode(newData.slice(offset, offset+size));
                offset += size;
                var rootNode = JSON.parse(jsonChunk);
                if(this.inputHandlers.length > 0) {
                    this.inputHandlers.forEach(handler => { handler(rootNode); });
                }
            }

            var endIngest = window.performance.now();
            this.log("Ingest time: " + (endIngest - this.endPoll));
            this.startPoll = window.performance.now();

            newData = null;
            rootNode = null;
            this.poll(); // long poll
        }.bind(this);
        xhr.addEventListener("error", function (e) {
            console.log("error: " + e);
        });
        xhr.send();
    }
}