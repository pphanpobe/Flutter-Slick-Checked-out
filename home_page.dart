import 'dart:convert';

import 'package:flutter/material.dart';
// import 'package:barras/barras.dart';
import 'barcode_reader/barcode_reader_overlay_painter.dart';

import 'package:vibration/vibration.dart';
import 'package:flutter_beep/flutter_beep.dart';
import 'package:qrcode/qrcode.dart';
import 'package:http/http.dart' as http;
import 'dart:convert' as convert;
import './barcode_reader/qrcode.dart' as qr;

class HomePage extends StatefulWidget {
  @override
  _HomePageState createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  @override
  void initState() {
    super.initState();
    startScan();
  }

  void startScan() {
    _captureController.onCapture((data) {
      _captureController.pause();

      if (data != "") getDetail(data);
      // Future.delayed(const Duration(milliseconds: 1500), () {
      //   _captureController.resume();
      // });
    });
  }

  List<BarCodeDetail> _barcodeList = [];
  List<Map<String, dynamic>> _barcodeListEach = [];
  List<BarCodeDetail> _barcodeOrdered = [];
  List<Map<String, dynamic>> _showCart = [];
  qr.QRCaptureController _captureController = qr.QRCaptureController();
  bool _flagPress = false;
  // final _scrollController = ScrollController();
  String _scannedCode = "";
  bool _flagShowBarcodeNotFound = false;

  dynamic _summaryDetail = {
    "total_price": 0,
    "total_vat": 0,
    "total_discount": 0,
    "total_payment": 0,
    "total_diff": 0
  };

  Widget _buildCaptureView() {
    return qr.QRCaptureView(
      controller: _captureController,
    );
  }

  Widget _buildViewfinder(BuildContext context) {
    return CustomPaint(
      size: MediaQuery.of(context).size,
      painter: BarcodeReaderOverlayPainter(
        // drawBorder: true,
        // viewfinderWidth: 240.0,
        // viewfinderHeight: 160.0,
        // borderRadius: 16.0,
        // scrimColor: Colors.black54,
        // borderColor: Colors.green,
        // borderStrokeWidth: 4.0,
        drawBorder: true,
        viewfinderHeight: 120.0,
        viewfinderWidth: 300.0,
        scrimColor: Color.fromRGBO(128, 0, 0, 0.5),
        borderColor: Colors.red,
        borderRadius: 24.0,
        borderStrokeWidth: 2.0,
      ),
    );
  }

  Widget _buildNotiBarcodeNotFound(BuildContext context) {
    if (_flagShowBarcodeNotFound == true) {
      return Container(
          color: Colors.black,
          width: MediaQuery.of(context).size.width,
          height: MediaQuery.of(context).size.height,
          child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: <Widget>[
                new Text("ไม่พบบาร์โค้ด",
                    style: TextStyle(color: Colors.red, fontWeight: FontWeight.bold, fontSize: 25))
              ]));
    } else {
      return Container();
    }
  }

  Widget _buildViewfinderGreen(BuildContext context) {
    return CustomPaint(
      size: MediaQuery.of(context).size,
      painter: BarcodeReaderOverlayPainter(
        // drawBorder: true,
        // viewfinderWidth: 240.0,
        // viewfinderHeight: 160.0,
        // borderRadius: 16.0,
        // scrimColor: Colors.black54,
        // borderColor: Colors.green,
        // borderStrokeWidth: 4.0,
        drawBorder: true,
        viewfinderHeight: 120.0,
        viewfinderWidth: 300.0,
        scrimColor: Color.fromRGBO(0, 128, 7, 0.5),
        borderColor: Colors.green,
        borderRadius: 24.0,
        borderStrokeWidth: 2.0,
      ),
    );
  }

  Widget _testBtnScan() {
    return RaisedButton(
      color: Colors.blueAccent,
      textColor: Colors.white,
      onPressed: () {
        Vibration.vibrate(duration: 100);
        setState(() {
          _flagPress = true;
        });
        _captureController.onCapture((data) {
          if (_flagPress) {
            FlutterBeep.beep();
            setState(() {
              _scannedCode = data;
              _flagPress = false;
            });
          }
        });
        if (_scannedCode != "") searchData(_scannedCode);
      },
      child: Text('SCANJA'),
    );
  }

  void searchData(barcode) {
    getDetail(barcode);
  }

  void getDetail(barcode) async {
    Vibration.vibrate(duration: 100);
    // _captureController.pause();
    var url = 'http://ppe-api-dev.central.co.th/v2/sku/' + barcode;
    final response = await http.get(url);
    if (response.bodyBytes.length != 0) {
      var jsonResponse = convert.jsonDecode(utf8.decode(response.bodyBytes));

      var jbarcode = jsonResponse['barcode'];
      var jname = jsonResponse['name'];
      Map dataArray = {"barcode": "$jbarcode", "name": "$jname", "qty": 1};
      var scannedBarcodedStr = BarCodeDetail.fromJson(dataArray);

      bool flagFound = false;

      List<BarCodeDetail> yourDataModelFromJson(String str) =>
          List<BarCodeDetail>.from(json.decode(str).map((x) => BarCodeDetail.fromJson(x)));

      if (this._barcodeList.length != 0) {
        final yourDataModel2 = yourDataModelFromJson(jsonEncode(this._barcodeList));
        for (int i = 0; i < yourDataModel2.length; i++) {
          if (yourDataModel2[i].barcode == barcode) {
            yourDataModel2[i].qty++;
            flagFound = true;
          }
        }
        if (!flagFound) {
          yourDataModel2.add(scannedBarcodedStr);
        }
        setState(() {
          _barcodeList = yourDataModel2;
        });
      } else {
        this._barcodeList.add(BarCodeDetail.fromJson(dataArray));
      }
      _barcodeOrdered.add(scannedBarcodedStr);
      var jsonStr = jsonEncode(_barcodeList);

      getPromotion(jsonStr);
    } else {
      setState(() {
        _flagShowBarcodeNotFound = true;
      });

      Future.delayed(const Duration(milliseconds: 500), () {
        _captureController.resume();
      });

      Future.delayed(const Duration(milliseconds: 2000), () {
        setState(() {
          _flagShowBarcodeNotFound = false;
        });
      });
    }
  }

  void getPromotion(jsonStr) async {
    var time1 = DateTime.now().millisecondsSinceEpoch;
    String url = 'http://ppe-api-dev.central.co.th/v2/cart';

    final Map<String, dynamic> data = Map.from({
      "business_date": "2021-12-22T08:28:54Z",
      "transaction_date": "2021-03-01T14:03:37.976589048Z",
      "store_id": "online",
      "staff_id": "ppe-api",
      "channel_id": "jd-central",
      "order_id": "sample",
      "item": []
    });
    var result = GetPromotionClass.fromJson(data, _barcodeList);

    var body = json.encode(result);
    var response = await http.post(url, headers: {"Content-Type": "application/json"}, body: body);
    // print(DateTime.now().millisecondsSinceEpoch - time1);
    var cartDetail = (jsonDecode(utf8.decode(response.bodyBytes))["cart"] as List)
        .map((e) => e as Map<String, dynamic>)
        ?.toList();
    var summaryDetail = (jsonDecode(utf8.decode(response.bodyBytes))["summary"]);

    var _barcodeListDetail = (jsonDecode(jsonEncode(_barcodeList)) as List)
        .map((e) => e as Map<String, dynamic>)
        ?.toList();

    getMap(_barcodeListDetail, cartDetail);
    setState(() {
      _summaryDetail = summaryDetail;
    });
    Future.delayed(const Duration(milliseconds: 100), () {
      _captureController.resume();
    });
  }

  List<Map<String, dynamic>> getMap(
      List<Map<String, dynamic>> description, List<Map<String, dynamic>> promotion) {
    List<Map<String, dynamic>> listOne = description;
    List<Map<String, dynamic>> listTwo = promotion;
    List<Map<String, dynamic>> result = listOne
        .map((e) => {
              'barcode': e['barcode'],
              'name': e['name'],
              'price': listTwo
                      .where((element) =>
                          element['barcode'].toString().compareTo(e['barcode'].toString()) == 0)
                      ?.first['price'] ??
                  '',
              'qty': listTwo
                      .where((element) =>
                          element['barcode'].toString().compareTo(e['barcode'].toString()) == 0)
                      ?.first['qty'] ??
                  '',
              'discount': listTwo
                      .where((element) =>
                          element['barcode'].toString().compareTo(e['barcode'].toString()) == 0)
                      ?.first['discount'] ??
                  '',
              'total': listTwo
                      .where((element) =>
                          element['barcode'].toString().compareTo(e['barcode'].toString()) == 0)
                      ?.first['total'] ??
                  '',
              'promotion': listTwo
                          .where((element) =>
                              element['barcode'].toString().compareTo(e['barcode'].toString()) == 0)
                          ?.first['promotion']
                          .length ==
                      0
                  ? null
                  : listTwo.first['promotion'].first['desc']['promotion'],
              'discountQty': listTwo
                          .where((element) =>
                              element['barcode'].toString().compareTo(e['barcode'].toString()) == 0)
                          ?.first['promotion']
                          .length ==
                      0
                  ? 0
                  : listTwo
                      .where((element) =>
                          element['barcode'].toString().compareTo(e['barcode'].toString()) == 0)
                      ?.first['promotion']
                      .first['discount']
                      .first['qty'],
              'amountDiscountEach': listTwo
                          .where((element) =>
                              element['barcode'].toString().compareTo(e['barcode'].toString()) == 0)
                          ?.first['promotion']
                          .length ==
                      0
                  ? ''
                  : listTwo
                      .where((element) =>
                          element['barcode'].toString().compareTo(e['barcode'].toString()) == 0)
                      ?.first['promotion']
                      .first['discount']
                      .first['amount'],
            })
        .toList();

    List<Map<String, dynamic>> temp = [];
    List<PromotionDiscountModel> yourDataModelFromJson(String str) =>
        List<PromotionDiscountModel>.from(
            json.decode(str).map((x) => PromotionDiscountModel.fromJson(x)));
    final resultModel = yourDataModelFromJson(jsonEncode(result));

    if (resultModel.length != 0) {
      for (int i = 0; i < resultModel.length; i++) {
        int j = 0;

        while (j < resultModel[i].qty.toInt()) {
          if (resultModel[i].discountQty <= 0) {
            resultModel[i].amountDiscountEach = '';
            resultModel[i].promotion = "";
          }
          var obj = {
            "barcode": resultModel[i].barcode,
            "name": resultModel[i].name,
            "price": resultModel[i].price,
            "promotion": resultModel[i].promotion,
            "amountDiscountEach": resultModel[i].amountDiscountEach,
          };

          temp.add(obj);
          j++;
          resultModel[i].discountQty--;
        }
      }
    }
    setState(() {
      _barcodeListEach = temp;
    });
    print('_barcodeListEach :(' +
        _barcodeListEach.length.toString() +
        ') ' +
        jsonEncode(_barcodeListEach).toString());
    print('_barcodeOrdered : (' +
        _barcodeOrdered.length.toString() +
        ') ' +
        jsonEncode(_barcodeOrdered).toString());
    final hh = yourDataModelFromJson(jsonEncode(_barcodeOrdered));
    final aa = yourDataModelFromJson(jsonEncode(_barcodeListEach));
    for (int k = 0; k < hh.length; k++) {
      for (int m = aa.length - 1; m >= 0; m--) {
        if (hh[k].barcode == aa[m].barcode) {
          hh[k].promotion = aa[m].promotion;
          hh[k].amountDiscountEach = aa[m].amountDiscountEach;
          hh[k].price = aa[m].price;
          aa.removeAt(m);
          break;
        }
      }
    }
    print('final : :' + jsonEncode(hh).toString());
    setState(() {
      _showCart =
          (jsonDecode(jsonEncode(hh)) as List).map((e) => e as Map<String, dynamic>)?.toList();
      _scrollController.animateTo(MediaQuery.of(context).size.height,
          curve: Curves.easeOut, duration: const Duration(milliseconds: 300));
    });
    return result;
  }

  ScrollController _scrollController = new ScrollController();
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text("Barcode Scanner Page"),
      ),
      body: Container(
        color: Colors.grey[400],
        child: Column(
          children: <Widget>[
            Container(
              width: double.infinity,
              color: Colors.brown[400],
              child: Column(
                children: <Widget>[
                  Container(
                    height: 70,
                    child: Text('Process '),
                  ),
                ],
              ),
            ),
            Container(
                width: double.infinity,
                height: 50,
                child: new Row(
                    crossAxisAlignment: CrossAxisAlignment.center,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: <Widget>[
                      new Text('กรุณาสแกนสินค้า',
                          style: TextStyle(
                              color: Colors.green[900], fontWeight: FontWeight.bold, fontSize: 20)),
                    ])),
            Container(
              child: Column(
                children: <Widget>[
                  Container(
                    width: 300,
                    height: 180,
                    child: Stack(
                      alignment: Alignment.center,
                      children: <Widget>[
                        _buildCaptureView(),
                        _buildViewfinder(context),
                        _buildNotiBarcodeNotFound(context)
                      ],
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(top: 20),
                child: SafeArea(
                  child: Column(
                    children: <Widget>[
                      Container(
                        width: 300,
                        color: Colors.red[700],
                        child: Padding(
                          padding:
                              const EdgeInsets.only(left: 8.0, top: 5.0, right: 8.0, bottom: 10.0),
                          child: Column(
                            children: <Widget>[
                              Container(
                                  child: Row(
                                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                      children: [
                                    new Text(
                                      "รายการสินค้า (" + _showCart.length.toString() + ")",
                                      style: TextStyle(
                                          color: Colors.white,
                                          fontWeight: FontWeight.bold,
                                          fontSize: 18),
                                    )
                                  ])),
                            ],
                          ),
                        ),
                      ),
                      Container(
                        color: Colors.white,
                        // decoration: BoxDecoration(border: Border.all(color: Colors.grey)),
                        height: 200,
                        width: 300,
                        child: Scrollbar(
                          isAlwaysShown: true,
                          child: ListView.builder(
                              controller: _scrollController,
                              reverse: false,
                              shrinkWrap: true,
                              scrollDirection: Axis.vertical,
                              itemCount: _showCart == null ? null : _showCart.length,
                              itemBuilder: (context, index) {
                                return Container(
                                  width: 300,
                                  color: Colors.white,
                                  child: Padding(
                                    padding: EdgeInsets.only(
                                        left: 8.0,
                                        top: 0.0,
                                        right: 8.0,
                                        bottom:
                                            (_showCart[index]['amountDiscountEach'].toString() == ''
                                                ? 0
                                                : 2.0)),
                                    child: Column(
                                      children: <Widget>[
                                        Container(
                                            child: Row(
                                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                                children: [
                                              new Text(_showCart[index]['name'].toString(),
                                                  style: new TextStyle(
                                                      fontWeight: FontWeight.bold, fontSize: 14.0)),
                                              new Text(_showCart[index]['price'].toStringAsFixed(2),
                                                  style: new TextStyle(
                                                      fontWeight: FontWeight.bold, fontSize: 14.0)),
                                            ])),
                                        Container(
                                            child: Row(
                                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                                children: [
                                              new Text(_showCart[index]['promotion'].toString(),
                                                  style: new TextStyle(
                                                      fontWeight: FontWeight.bold,
                                                      color: Colors.red,
                                                      fontSize: 12.0)),
                                              new Text(
                                                  _showCart[index]['amountDiscountEach']
                                                              .toString() ==
                                                          ''
                                                      ? ''
                                                      : _showCart[index]['amountDiscountEach']
                                                          .toStringAsFixed(2),
                                                  style: new TextStyle(
                                                      color: Colors.red,
                                                      fontWeight: FontWeight.bold,
                                                      fontSize: 14.0))
                                            ])),
                                        new Divider()
                                      ],
                                    ),
                                  ),
                                );
                              }),
                        ),
                      ),
                      Container(
                        width: 300,
                        color: Colors.red[700],
                        child: Padding(
                          padding:
                              const EdgeInsets.only(left: 8.0, top: 2.0, right: 8.0, bottom: 2.0),
                          child: Column(
                            children: <Widget>[
                              Container(
                                  child: Row(
                                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                      children: [
                                    new Text(
                                      "ยอดรวม",
                                      style: TextStyle(
                                        color: Colors.white,
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                    new Text(_summaryDetail['total_price'].toStringAsFixed(2),
                                        style: TextStyle(
                                          color: Colors.white,
                                          fontWeight: FontWeight.bold,
                                        ))
                                  ])),
                              Container(
                                  child: Row(
                                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                      children: [
                                    new Text("ส่วนลด",
                                        style: TextStyle(
                                          color: Colors.white,
                                          fontWeight: FontWeight.bold,
                                        )),
                                    new Text(_summaryDetail['total_discount'].toStringAsFixed(2),
                                        style: TextStyle(
                                          color: Colors.white,
                                          fontWeight: FontWeight.bold,
                                        ))
                                  ])),
                              Container(
                                  child: Row(
                                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                      children: [
                                    new Text("ยอดรวมสุทธิ",
                                        style: TextStyle(
                                          color: Colors.white,
                                          fontWeight: FontWeight.bold,
                                          decoration: TextDecoration.underline,
                                        )),
                                    new Text(_summaryDetail['total_diff'].toStringAsFixed(2),
                                        style: TextStyle(
                                          color: Colors.white,
                                          fontWeight: FontWeight.bold,
                                          decoration: TextDecoration.underline,
                                        ))
                                  ])),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            Container(
              height: 100,
              // color: Colors.green,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  OutlinedButton(
                    child: Text('ดำเนินการต่อ'),
                    style: OutlinedButton.styleFrom(
                      primary: Colors.white,
                      backgroundColor: Colors.teal,
                    ),
                    onPressed: () {
                      print('Pressed');
                    },
                  )
                ],
              ),
            )
          ],
        ),
      ),
    );
  }
}

//////////////////////////////////////////////////////////////////////
class Autogenerated {
  List<Cart> cart;
  Summary summary;

  Autogenerated({this.cart, this.summary});

  Autogenerated.fromJson(Map<String, dynamic> json) {
    if (json['cart'] != null) {
      cart = new List<Cart>();
      json['cart'].forEach((v) {
        cart.add(new Cart.fromJson(v));
      });
    }
    summary = json['summary'] != null ? new Summary.fromJson(json['summary']) : null;
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = new Map<String, dynamic>();
    if (this.cart != null) {
      data['cart'] = this.cart.map((v) => v.toJson()).toList();
    }
    if (this.summary != null) {
      data['summary'] = this.summary.toJson();
    }
    return data;
  }
}

class Cart {
  String barcode;
  int qty;
  int price;
  int discount;
  int total;
  List<Promotion> promotion;

  Cart({this.barcode, this.qty, this.price, this.discount, this.total, this.promotion});

  Cart.fromJson(Map<String, dynamic> json) {
    barcode = json['barcode'];
    qty = json['qty'];
    price = json['price'];
    discount = json['discount'];
    total = json['total'];
    if (json['promotion'] != null) {
      promotion = new List<Promotion>();
      json['promotion'].forEach((v) {
        promotion.add(new Promotion.fromJson(v));
      });
    }
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = new Map<String, dynamic>();
    data['barcode'] = this.barcode;
    data['qty'] = this.qty;
    data['price'] = this.price;
    data['discount'] = this.discount;
    data['total'] = this.total;
    if (this.promotion != null) {
      data['promotion'] = this.promotion.map((v) => v.toJson()).toList();
    }
    return data;
  }
}

class Promotion {
  String campaign;
  String name;
  Desc desc;
  List<Discount> discount;

  Promotion({this.campaign, this.name, this.desc, this.discount});

  Promotion.fromJson(Map<String, dynamic> json) {
    campaign = json['campaign'];
    name = json['name'];
    desc = json['desc'] != null ? new Desc.fromJson(json['desc']) : null;
    if (json['discount'] != null) {
      discount = new List<Discount>();
      json['discount'].forEach((v) {
        discount.add(new Discount.fromJson(v));
      });
    }
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = new Map<String, dynamic>();
    data['campaign'] = this.campaign;
    data['name'] = this.name;
    if (this.desc != null) {
      data['desc'] = this.desc.toJson();
    }
    if (this.discount != null) {
      data['discount'] = this.discount.map((v) => v.toJson()).toList();
    }
    return data;
  }
}

class Desc {
  String campaign;
  String promotion;

  Desc({this.campaign, this.promotion});

  Desc.fromJson(Map<String, dynamic> json) {
    campaign = json['campaign'];
    promotion = json['promotion'];
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = new Map<String, dynamic>();
    data['campaign'] = this.campaign;
    data['promotion'] = this.promotion;
    return data;
  }
}

class Discount {
  int kind;
  int qty;
  int amount;

  Discount({this.kind, this.qty, this.amount});

  Discount.fromJson(Map<String, dynamic> json) {
    kind = json['kind'];
    qty = json['qty'];
    amount = json['amount'];
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = new Map<String, dynamic>();
    data['kind'] = this.kind;
    data['qty'] = this.qty;
    data['amount'] = this.amount;
    return data;
  }
}

class Summary {
  int totalPrice;
  int totalVat;
  int totalDiscount;
  int totalPayment;
  int totalDiff;

  Summary({this.totalPrice, this.totalVat, this.totalDiscount, this.totalPayment, this.totalDiff});

  Summary.fromJson(Map<String, dynamic> json) {
    totalPrice = json['total_price'];
    totalVat = json['total_vat'];
    totalDiscount = json['total_discount'];
    totalPayment = json['total_payment'];
    totalDiff = json['total_diff'];
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = new Map<String, dynamic>();
    data['total_price'] = this.totalPrice;
    data['total_vat'] = this.totalVat;
    data['total_discount'] = this.totalDiscount;
    data['total_payment'] = this.totalPayment;
    data['total_diff'] = this.totalDiff;
    return data;
  }
}

/////////////////////////////////////////////////////////////////////
///
///
class PromotionDiscountModel {
  PromotionDiscountModel(
      {this.barcode,
      this.qty,
      this.discountQty,
      this.amountDiscountEach,
      this.price,
      this.promotion});

  PromotionDiscountModel.fromJson(Map<dynamic, dynamic> json) {
    barcode = json['barcode'];
    name = json['name'];
    qty = json['qty'];
    price = json['price'];
    discountQty = json['discountQty'];
    amountDiscountEach = json['amountDiscountEach'];
    promotion = json['promotion'];
  }

  String barcode;
  String name;
  int qty;
  var discountQty;
  var amountDiscountEach;
  var price;
  var promotion;

  Map<dynamic, dynamic> toJson() {
    final Map<dynamic, dynamic> data = new Map<dynamic, dynamic>();
    data['barcode'] = this.barcode;
    data['name'] = this.name;
    data['qty'] = this.qty;
    data['discountQty'] = this.discountQty;
    data['amountDiscountEach'] = this.amountDiscountEach;
    data['price'] = this.price;
    data['promotion'] = this.promotion;
    return data;
  }
}

class BarCodeDetail {
  BarCodeDetail({this.barcode, this.qty});

  BarCodeDetail.fromJson(Map<dynamic, dynamic> json) {
    barcode = json['barcode'];
    name = json['name'];
    qty = json['qty'];
  }

  String barcode;
  String name;
  int qty;

  Map<dynamic, dynamic> toJson() {
    final Map<dynamic, dynamic> data = new Map<dynamic, dynamic>();
    data['barcode'] = this.barcode;
    data['name'] = this.name;
    data['qty'] = this.qty;
    return data;
  }
}

class GetPromotionClass {
  GetPromotionClass(
      {this.business_date,
      this.transaction_date,
      this.store_id,
      this.staff_id,
      this.channel_id,
      this.order_id,
      this.item,
      this.list});

  GetPromotionClass.fromJson(Map<String, dynamic> json, List<BarCodeDetail> listDetail) {
    business_date = json['business_date'];
    transaction_date = json['transaction_date'];
    store_id = json['store_id'];
    staff_id = json['staff_id'];
    channel_id = json['channel_id'];
    order_id = json['order_id'];
    item = json['item'];

    for (var v in listDetail) {
      item.add(v.toJson());
    }
  }

  String business_date;
  String channel_id;
  List item = [];
  List<BarCodeDetail> list = [];
  String order_id;
  String staff_id;
  String store_id;
  String transaction_date;

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = new Map<String, dynamic>();
    data['business_date'] = this.business_date;
    data['transaction_date'] = this.transaction_date;
    data['store_id'] = this.store_id;
    data['staff_id'] = this.staff_id;
    data['channel_id'] = this.channel_id;
    data['order_id'] = this.order_id;
    data['item'] = this.item;
    return data;
  }
}
