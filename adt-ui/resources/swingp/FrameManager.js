class FrameManager {
    constructor(chartComponent, canvasComponent) {
        // Function callback bindings
        this.togglePause = this.togglePause.bind(this);
        this.drawChart = this.drawChart.bind(this);
        this.draw = this.draw.bind(this);
        this.processInput = this.processInput.bind(this);
        this.selectHandler = this.selectHandler.bind(this);

        // Draw params
        this.chartComponent = chartComponent;
        this.pause = false;
        this.redraw = false;
        this.frameNumber = 0;
        this.maxCount = 250;
        this.frame_tree_canvas = canvasComponent;
        this.ctx = this.frame_tree_canvas.getContext("2d");

        // Data params
        this.frameData = [];
        this.allFrames = [];

        google.charts.load('current', { 'packages': ['bar'] });
        google.charts.setOnLoadCallback(this.drawChart);
    }

    togglePause() {
        this.pause = !this.pause;
    }

    setMaxFrames(val) {
        this.maxCount = val;
    }

    drawChart() {
        this.chart = new google.charts.Bar(this.chartComponent);
        google.visualization.events.addListener(this.chart, 'select', this.selectHandler);
    }

    selectHandler(element) {
        if (this.chart.getSelection().length > 0) {
            this.renderTree();
        }
    }

    buildName(type, name) {
        var char = "";
        if (type == "PaintImmediatelyMethodStat") {
            char = "[I]";
        } else if (type == "PaintComponentMethodStat") {
            char = "[P]";
        } else if (type == "PaintChildrenMethodStat") {
            char = "[C]";
        }
        return char + name;
    }

    buildTree(output, node, indent) {
        node.callee.forEach(child => {
            var time = ((child.endTime - child.startTime) / 100000.0);
            output.push({
                "name": child.owner,
                "duration": time,
                "indent": indent,
                "type": child.classType,
            });
            this.buildTree(output, child, indent + 1);
        });
    }

    renderTree() {
        var frameData = this.allFrames[this.chart.getSelection()[0].row];
        this.ctx.font = "12px Arial";
        this.ctx.clearRect(0, 0, frame_tree_canvas.width, frame_tree_canvas.height);
        this.ctx.fillStyle = 'rgb(0,0,0)';
        var offset = 0;
        var rows = 1;
        frameData.forEach(data => {
            rows++;
            rows += data.children.length;
        });
        this.ctx.canvas.height = rows * 15;
        frameData.forEach(data => {
            this.ctx.fillText(data.duration, offset, 15);
            this.ctx.fillText(this.buildName(data.type, data.name), offset + 75, 15);
            var row = 2;
            var maxIndent = 0;
            data.children.forEach(child => {
                if (child.indent > maxIndent) {
                    maxIndent = child.indent;
                }

                this.ctx.fillText(child.duration, offset, row * 15);
                this.ctx.fillText(this.buildName(child.type, child.name), 75 + offset + child.indent * 10, row * 15);
                row++;
            });
            offset += maxIndent * 10 + 100;
        });
    }



    draw() {
        if (this.frameData.length == 0 || this.chart == null || !this.redraw || this.pause) {
            return;
        }
        while (this.allFrames.length > this.maxCount) {
            this.allFrames.shift();
        }
        this.redraw = false;
        this.allFrames.push(this.frameData.slice());
        var maxSize = this.allFrames[0].length;
        var maxElement = this.allFrames[0];

        // Find any root nodes that may have more than one child.
        this.allFrames.forEach(val => {
            if (maxSize < val.length) {
                maxSize = val.length;
                maxElement = val;
            }
        });

        // Build our chart data.
        var data = new google.visualization.DataTable();
        data.addColumn('number', "Count");
        maxElement.forEach(val => {
            data.addColumn('number', val.name);
        })

        this.allFrames.forEach(val => {
            var frameData = []
            frameData.push(this.frameNumber);
            frameData.push(val[0].duration);
            while (frameData.length < this.maxSize) {
                frameData.push(0);
            }
            data.addRow(frameData);
        });

        var options = {
            title: 'Frame Performance',
            legend: { position: 'bottom' }
        };
        this.chart.draw(data, options);
    }


    // Process each complete JSON tree, and split up ThreadStats individually for processing.
    processInput(rootNode) {
        //var rootFilter = rootFilterElement.value;
        var output = [];
        this.frameNumber++;
        rootNode.forEach(val => {
            if (val.events.length > 0) {
                var time = ((val.events[0].endTime - val.events[0].startTime) / 100000.0);
                var children = [];
                this.buildTree(children, val.events[0], 1);
                output.push({
                    "name": val.threadName.substring(0, Math.min(val.threadName.length, 25)),
                    "duration": time,
                    "time": this.frameNumber,
                    "children": children,
                    "type": val.events[0].classType,
                });
            }
        })
        if (output.length > 0) {
            this.redraw = true;
            this.frameData = output;
        }
    }
}