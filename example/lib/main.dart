import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:wifi/wifi.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import 'package:material_design_icons_flutter/material_design_icons_flutter.dart';

void main() => runApp(new MyApp());

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      title: 'Wifi',
      theme: new ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: new MyHomePage(),
    );
  }
}

class MyHomePage extends StatefulWidget {
  @override
  _MyHomePageState createState() => new _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {

  List wifiList = List();
  TextEditingController pass = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  _loadData() async {
    await Wifi.getListWifi().then((data) {
      print("Data $data");
      if(data.isNotEmpty || data != null) {
        var res = jsonDecode(data);
        setState(() {
          for(int i = 0; i < res.length; i++) {
            wifiList.add({
              'ssid':res[i]['ssid'],
              'status':res[i]['status'],
              'level':res[i]['level']
            });
          }
        });
      }
    });
  }

  _launchListWifi() async {
    await Wifi.list("").then((data) {
      for(int i = 0; i < data.length; i++) {
        WifiResult result = data[i];
        print(result.ssid);
      }
    });
  }

  Future<void> refresh() async {
    wifiList.clear();
    await Wifi.getListWifi().then((data) {
      print("Data $data");
      if(data.isNotEmpty || data != null) {
        var res = jsonDecode(data);
        setState(() {
          for(int i = 0; i < res.length; i++) {
            wifiList.add({
              'ssid':res[i]['ssid'],
              'status':res[i]['status'],
              'level':res[i]['level']
            });
          }
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Wifi'),
        centerTitle: true,
      ),
      body: Container(
        child: RefreshIndicator(
          onRefresh: refresh,
          child: ListView.builder(
            itemCount: wifiList.length,
            itemBuilder: (context, index) => _buildItem(index),
          ),
        ),
      )
    );
  }

  _buildItem(index) => ListTile(
    leading: Icon(_buildWifiIcon(wifiList[index]['level']), color: Colors.blue, size: 16,),
    title: Text(wifiList[index]['ssid']),
    subtitle: Text(wifiList[index]['status']),
    onTap: () {
      _showDialog(index);
    },
  );

  _showDialog(int index) {
    showDialog(
      context: context,
      builder: (context) => Center(
        child: AlertDialog(
          content: TextField(
            autofocus: true,
            controller: pass,
          ),
          title: Text(wifiList[index]['ssid'],),
          actions: <Widget>[
            FlatButton(
              child: Text("CONNECT"),
              onPressed: () async {
                WifiState state = await Wifi.connection(wifiList[index]['ssid'], pass.text.trim());
                print("state index : ${state.index}");
                Navigator.of(context, rootNavigator: true).pop();
              },
            ),
            FlatButton(
              child: Text("CANCEL"),
              onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
            )
          ],
        ),
      )
    );
  }

 _buildWifiIcon(int level) {
    IconData icon;
    switch(level) {
      case 1: icon = MdiIcons.wifiStrength1;
        break;
      case 2: icon = MdiIcons.wifiStrength2;
        break;
      case 3: icon = MdiIcons.wifiStrength3;
        break;
      default: icon = MdiIcons.wifi;     
    }
    return icon;
  }
}
