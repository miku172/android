layui.use(['table', 'element'], function () {
    var $ = layui.$;
    var table = layui.table;
    var layer = layui.layer;
    var element = layui.element;

    var pie = createMemoryPie();
    var line = createMemtoryStackedLine();
    var thread = {
        timer: null,
        start: function () {
            thread.stop();
            pie.resize();
            line.resize();
            setSystemInfo($, pie, line);
            this.timer = setInterval(() => {
                setSystemInfo($, pie, line);
            }, 5000);
        },
        stop: function () {
            if (this.timer) {
                clearInterval(this.timer);
                this.timer = null;
            }
        }
    }
    window.onbeforeunload = function(){
        thread.stop();
    }
    element.on('nav(lay-menu)', function (obj) {
        var text = obj.text();
        if (text === "日志列表") {
            $("#layuiBody").css("display", "block");
            $("#layuiBody2").css("display", "none");
            thread.stop();
        } else if (text === "系统概况") {
            $("#layuiBody").css("display", "none");
            $("#layuiBody2").css("display", "block");
            thread.start();
        }
    })
    table.render({
        elem: '#table',
        url: '/Api/System.Core/GetList',
        toolbar: true,
        defaultToolbar: ['filter'],
        height: () => {
            var otherHeight = $('#layuiBody').outerHeight();
            return otherHeight - 75;
        },
        css: '.layui-table-tool-temp{padding-right: 145px;}',
        cellMinWidth: 80,
        totalRow: true,
        page: true,
        cols: [[
            { field: 'type', width: 120, title: '类别', templet: d => d.type === "directory" ? "文件夹" : "文件" },
            { field: 'name', title: '名称' },
            { field: 'size', title: '大小', width: 120 },
            { fixed: 'right', title: '操作', width: 134, minWidth: 125, toolbar: '#options' }
        ]],
        initSort: {
            field: 'name',
            type: 'desc'
        },
        error: function (res, msg) {
            layer.msg(msg);
        }
    });
    table.on('tool(table)', function (obj) {
        var data = obj.data;
        var layEvent = obj.event;
        var title = data.name;
        if (layEvent === 'cat') {
            layer.open({
                title: title + "日志",
                type: 1,
                resize: false,
                move: false,
                area: ['100vw', '100vh'],
                content: '<table class="layui-hide" id="table2" lay-filter="table2"></table>',
                success: function (layero, index, that) {
                    var htmlHeight = layero[0].children[1].clientHeight;
                    var htmlWidth = layero[0].children[1].clientWidth;
                    layero[0].children[1].style.marginLeft = "15px";
                    layero[0].children[1].style.marginTop = "15px";
                    table.render({
                        elem: '#table2',
                        url: '/Api/System.Core/GetListByDate',
                        where: { date: encodeURIComponent(title) },
                        toolbar: true,
                        defaultToolbar: ['filter'],
                        width: htmlWidth - 30,
                        height: htmlHeight - 30,
                        css: '.layui-table-tool-temp{padding-right: 145px;}',
                        cellMinWidth: 80,
                        totalRow: true,
                        page: true,
                        cols: [[
                            { field: 'type', width: 120, title: '类别', templet: d => d.type === "directory" ? "文件夹" : "文件" },
                            { field: 'name', title: '名称' },
                            { field: 'size', title: '大小', width: 120 },
                            { fixed: 'right', title: '操作', width: 134, minWidth: 125, toolbar: '#options' }
                        ]],
                        initSort: {
                            field: 'name',
                            type: 'asc'
                        },
                        error: function (res, msg) {
                            layer.msg(msg);
                        }
                    });
                    table.on('tool(table2)', function (obj) {
                        var data = obj.data;
                        var layEvent = obj.event;
                        var title2 = data.name;
                        if (layEvent === 'cat') {
                            layer.open({
                                title: title2 + "日志",
                                type: 1,
                                resize: false,
                                move: false,
                                area: ['100vw', '100vh'],
                                content: '<div id="detail" class="layui-clear-space"></div>',
                                success: async function (layero, index, that) {
                                    GetInfoByName(title, title2);
                                }
                            });
                        } else if (layEvent === 'del') {
                            var a = deleteFileByName(title, title2);
                            if (a) {
                                table.reloadData('table2', {
                                    scrollPos: 'fixed'
                                });
                            }
                        }
                    });
                }
            });
        } else if (layEvent === 'del') {
            var a = deleteDirectoryByDate(title);
            if (a) {
                table.reloadData('table', {
                    scrollPos: 'fixed'
                });
            }
        }
    });
    //
});
// 删除莫一个具体文件
async function deleteDirectoryByDate(date) {
    try {
        var http = await fetch(`/Api/System.Core/DeleteDirectoryByDate?date=${encodeURIComponent(date)}`);
        var res = await http.json();
        layer.msg(res.msg);
        return res.code === 0;
    } catch (error) {
        console.error(error);
        layer.msg('请求失败');
    }
    return false;
}
// 删除莫一个具体文件
async function deleteFileByName(date, name) {
    try {
        var http = await fetch(`/Api/System.Core/DeleteFileByName?date=${encodeURIComponent(date)}&name=${encodeURIComponent(name)}`);
        var res = await http.json();
        layer.msg(res.msg);
        return res.code === 0;
    } catch (error) {
        console.error(error);
        layer.msg('请求失败');
    }
    return false;
}
// 获取详情数据
async function GetInfoByName(date, name) {
    try {
        var http = await fetch(`/Api/System.Core/GetInfoByName?date=${encodeURIComponent(date)}&name=${encodeURIComponent(name)}`);
        var res = await http.json();
        if (res.code != 0) {
            layer.msg(res.msg);
            return;
        }
        var d = "";
        for (let item of res.data) {
            let color = "info";
            if (item.type === 'DEBUG') {
                color = 'debug';
            } else if (item.type === 'INFO') {
                color = 'info';
            } else if (item.type === 'WARN') {
                color = 'warn';
            } else if (item.type === 'ERROR') {
                color = 'error';
            }
            d += createPaneItem(item.time, item.tag, item.package, item.type, color, item.value);
        }
        document.querySelector("#detail").innerHTML = d;
    } catch (error) {
        console.error(error);
        layer.msg('请求失败');
    }
}
//
async function setSystemInfo($, pie, line) {
    var data = await getSystemInfo();
    if (!data) return;
    //
    $("#model").text(data.model);
    $("#screenSize").text(`${data.screenWidth} x ${data.screenHeight}`);
    $("#memory").text(`${data.availableMemory} / ${data.totalMemory}`);
    $("#storage").text(`${data.availableStorage} / ${data.totalStorage}`);
    //
    pie.setOption({
        series: [
            {
                data: [
                    { value: parseFloat(data.availableMemory).toFixed(2), name: '可用' },
                    { value: (parseFloat(data.totalMemory) - parseFloat(data.availableMemory)).toFixed(2), name: '已用' }
                ]
            }
        ]
    });
    //
    var xAxisData = line.getOption().xAxis[0].data;
    if (xAxisData.length > 7) {
        xAxisData = xAxisData.splice(1);
    }
    xAxisData.push(data.time.replace(/^\d{4}-\d{2}-\d{2} \d{2}:/, ''));
    //
    var maxMem = line.getOption().series[0].data;
    if (maxMem.length > 7) {
        maxMem = maxMem.splice(1);
    }
    maxMem.push(parseFloat(data.appMaxMemory).toFixed(2));
    //
    var totalMem = line.getOption().series[1].data;
    if (totalMem.length > 7) {
        totalMem = totalMem.splice(1);
    }
    totalMem.push(parseFloat(data.appTotalMemory).toFixed(2));
    //
    var freeMem = line.getOption().series[2].data;
    if (freeMem.length > 7) {
        freeMem = freeMem.splice(1);
    }
    freeMem.push(parseFloat(data.appFreeMemory).toFixed(2));

    line.setOption({
        xAxis: {
            data: xAxisData
        },
        series: [
            {
                name: '最大内存',
                type: 'line',
                data: maxMem.map(e=> Number(e))
            },
            {
                name: '总内存',
                type: 'line',
                data: totalMem.map(e=> Number(e))
            },
            {
                name: '空闲内存',
                type: 'line',
                data: freeMem.map(e=> Number(e))
            }
        ]
    });
}
// 获取系统信息
async function getSystemInfo() {
    try {
        var http = await fetch(`/Api/System.Core/GetSystemInfo`);
        var res = await http.json();
        return res.data;
    } catch (error) {
        console.error(error);
        layer.msg('请求失败');
    }
    return null;
}
// 内存饼图
function createMemoryPie() {
    var chartDom = document.getElementById('pie');
    var myChart = echarts.init(chartDom);
    var option = {
        tooltip: {
            trigger: 'item'
        },
        legend: {
            top: '5%',
            left: 'center'
        },
        series: [
            {
                name: '系统内存',
                type: 'pie',
                radius: ['40%', '70%'],
                avoidLabelOverlap: false,
                itemStyle: {
                    borderRadius: 10,
                    borderColor: '#fff',
                    borderWidth: 2
                },
                label: {
                    show: false,
                    position: 'center'
                },
                emphasis: {
                    label: {
                        show: true,
                        fontSize: 40,
                        fontWeight: 'bold'
                    }
                },
                labelLine: {
                    show: false
                },
                data: [
                    { value: 0, name: '可用' },
                    { value: 0, name: '已用' }
                ]
            }
        ]
    };
    myChart.setOption(option);
    myChart.resize();
    return myChart;
}
// app内存占用折线图
function createMemtoryStackedLine() {
    var chartDom = document.getElementById('stackedLine');
    var myChart = echarts.init(chartDom);
    var option = {
        title: {
            text: 'app内存占用(MB)'
        },
        tooltip: {
            trigger: 'axis'
        },
        legend: {
            data: ['最大内存', '总内存', '空闲内存',]
        },
        grid: {
            left: '3%',
            right: '4%',
            bottom: '3%',
            containLabel: true
        },
        xAxis: {
            type: 'category',
            boundaryGap: false,
            data: []
        },
        yAxis: {
            type: 'value'
        },
        series: [
            {
                name: '最大内存',
                type: 'line',
                data: []
            },
            {
                name: '总内存',
                type: 'line',
                data: []
            },
            {
                name: '空闲内存',
                type: 'line',
                data: []
            }
        ]
    };
    myChart.setOption(option);
    return myChart;
}
//
function createPaneItem(time, activity, packageName, type, typeColor, message) {
    return `<div class="layui-panel">
    [<pre class="time">${time ? time : ''}</pre>]<pre class="split"></pre>[<pre class="activity">${activity ? activity : ''}</pre>]<pre class="split"></pre>
    [<pre class="package">${packageName ? packageName : ''}</pre>]<pre class="split"></pre>[<pre class="${typeColor}">${type ? type : 'INFO'}</pre>]<pre class="split"></pre>
    <pre class="message">${message ? message : ''}</pre>
</div>`;
}