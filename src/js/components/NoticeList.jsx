import React from 'react';
import Notice from './Notice.jsx';
import request from 'superagent';
import pubsub from 'pubsub-js';
import {Events, Category} from '../enums.js';

var ListItem = React.createClass({
  onclick: function() {
    pubsub.publish(Events.NAV, {action: Events.NOTICE, id: this.props.notice.id, category: this.props.category});
  },
  render: function() {
    return (
      <div>
        <i className="fi-clock"></i>
        <div onClick={this.onclick}>{this.props.notice.title}</div>
      </div>
    );
  }
});


export default React.createClass({
  getInitialState() {
    return {
    };
  },

  getDefaultProps() {
    return {
      notices: []
    }
  },

  render() {
    let loadingEl, noticesEl;
    let titleText = '';

    let {notices, category} = this.props;

    let className = 'harja-noticelist harja-' + category + '-noticelist';

    switch (category) {
      case Category.CARE:
        titleText = 'Ajankohtaista teiden hoidossa';
        break;
      case Category.MAINTENANCE:
        titleText = 'Ajankohtaista teiden ylläpidossa';
        break;
      case Category.FAQ:
        titleText = 'Usein Kysytyt Kysymykset';
        break;
    }

    if (!notices.length > 0) {
      loadingEl = (
        <img className="harja-loading" src="styles/images/ajax-loader.gif" alt="ladataan..." />
      );
    }
    else {
      noticesEl = (
        <ul>
          {notices.map(notice =>
            <ListItem notice={notice} key={notice.id} category={category}/>//<Notice notice={notice} key={notice.id} />
          )}
        </ul>
      );
    }

    return (
      <div className={className}>
        <h5>{titleText}</h5>
        {loadingEl}
        {noticesEl}
      </div>
    );
  }
});
