import React, {Component} from 'react';
import {
  AppRegistry,
  StyleSheet,
  Text,
  View,
  Platform,
  ScrollView,
  ListView,
  Image,
  TouchableOpacity,
  RecyclerViewBackedScrollView,
} from 'react-native';

import Assert from './assert.js'
import RNTEST from '../index.js'

export default class Reporter extends Component {

  constructor(props:any) {
    super(props)
    this.tests = {
      common : []
    }
    this.testGroups = ['common']
    this.ds = null
    this.updateDataSource()

  }

  componentWillUpdate(nextProps, nextState) {
    this.updateDataSource()
  }

  render() {

    return (
      <ListView
        style={styles.container}
        dataSource={this.ds}
        renderRow={this.renderTest.bind(this)}
        renderScrollComponent={props => <RecyclerViewBackedScrollView {...props} />}
        renderSectionHeader={(data, id) => {
          return (
            <View style={styles.sectionHeader}>
              <Text style={styles.sectionText}>{id}</Text>
            </View>
          )
        }}
      />)
  }

  renderTest(t) {
    let pass = true
    let foundActions = false
    let tests = RNTEST.TestContext.getTests()

    if(Array.isArray(t.result) && !t.expired) {
      t.result = t.result.map((r) => {
        if(r.type.name === 'Assert' || r.type.name === 'Info') {
          foundActions = true
          let comp = r.props.comparer ? r.props.comparer(r.props.expect, r.props.actual) : (r.props.actual === r.props.expect)
          pass = pass && comp
        }
        return React.cloneElement(r, {desc : r.key})
      })
    }
    if(tests[t.sn].running)
      t.status = 'running'
    else if(tests[t.sn].executed) {
      t.status = foundActions ? (pass ? 'pass' : 'fail') : 'skipped'
      t.status = t.expired ? 'timeout' : t.status
    }
    else
      t.status = 'waiting'

    return (
      <TouchableOpacity onPress={()=>{
          t.start(t.sn)
        }}>
        <View key={'rn-test-' + t.desc} style={{
          borderBottomWidth : 1.5,
          borderColor : '#DDD',
        }}>
          <View key={t.desc} style={{
            alignItems : 'center',
            flexDirection : 'row'
          }}>
            <Text style={[styles.badge, {flex : 1, borderWidth : 0, textAlign : 'left'}]}>{t.desc}</Text>
            <Text style={[styles.badge, this.getBadge(t.status)]}>{t.status}</Text>
          </View>
          <View key={t.desc + '-result'} style={{backgroundColor : '#F4F4F4'}}>
            {t.expand ? t.result : (t.status === 'pass' ? null : t.result)}
          </View>
        </View>
      </TouchableOpacity>)
  }

  updateDataSource() {
    this.tests = {
      common : []
    }
    this.testGroups = ['common']
    RNTEST.TestContext.getTests().forEach((t) => {
      if(t.group) {
        if(!this.tests[t.group]) {
          this.testGroups.push(t.group)
          this.tests[t.group] = []
        }
        this.tests[t.group].push(t)
      }
      else
        this.tests.common.push(t)
    })

    let listDataSource = new ListView.DataSource({
      rowHasChanged : (r1, r2) => r1 !== r2,
      sectionHeaderHasChanged: (s1, s2) => s1 !== s2
    })
    this.ds = listDataSource.cloneWithRowsAndSections(this.tests, this.testGroups)
  }

  getBadge(status: 'waiting' | 'running' | 'pass' | 'fail' | 'timeout') {
    return styles[status]
  }

}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  badge : {
    margin : 16,
    padding : 4,
    borderRadius : 4,
    borderWidth : 2,
    textAlign : 'center'
  },
  skipped: {
    borderColor : '#AAAAAA',
    color : '#AAAAAA'
  },
  sectionHeader : {
    padding : 16,
    backgroundColor : '#F4F4F4',
  },
  waiting: {
    borderColor : '#AAAAAA',
    color : '#AAAAAA'
  },
  pass: {
    borderColor : '#00a825',
    color : '#00a825'
  },
  running: {
    borderColor : '#e3c423',
    color : '#e3c423'
  },
  fail: {
    borderColor : '#ff0d0d',
    color : '#ff0d0d'
  },
  timeout: {
    borderColor : '#ff0d0d',
    color : '#ff0d0d'
  }
});
